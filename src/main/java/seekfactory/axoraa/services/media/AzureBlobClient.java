package seekfactory.axoraa.services.media;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * Minimal Azure Blob Storage client over the REST API, authenticated with service SAS tokens signed
 * by the account key. It avoids the Azure SDK (Netty, Reactor, Jackson 2) on a small shared plan.
 * Only the operations the media service needs: put, get, head, delete and read-only links.
 */
public final class AzureBlobClient {

    /** Storage service version the SAS and requests are signed for. */
    static final String VERSION = "2022-11-02";
    private static final DateTimeFormatter SAS_TIME = DateTimeFormatter.ISO_INSTANT;

    /** Blob metadata from a HEAD request. */
    public record Properties(long contentLength, String contentType) {}

    private final String account;
    private final byte[] key;
    private final String container;
    private final URI endpoint;
    private final HttpClient http;
    /** Signed protocol: https only, except a local http emulator. */
    private final String protocol;
    private final Clock clock;

    public AzureBlobClient(String account, String accountKey, String container, String endpoint) {
        this(account, accountKey, container, endpoint, Clock.systemUTC());
    }

    AzureBlobClient(String account, String accountKey, String container, String endpoint, Clock clock) {
        if (account == null || account.isBlank()) throw new IllegalStateException("app.media.storage=azure needs APP_MEDIA_AZURE_ACCOUNT");
        if (accountKey == null || accountKey.isBlank()) throw new IllegalStateException("app.media.storage=azure needs APP_MEDIA_AZURE_ACCOUNT_KEY");
        if (container == null || container.isBlank()) throw new IllegalStateException("app.media.storage=azure needs APP_MEDIA_AZURE_CONTAINER");
        this.account = account.trim();
        this.key = Base64.getDecoder().decode(accountKey.trim());
        this.container = container.trim();
        String base = endpoint == null || endpoint.isBlank() ? "https://" + this.account + ".blob.core.windows.net" : endpoint.trim();
        this.endpoint = URI.create(base.endsWith("/") ? base.substring(0, base.length() - 1) : base);
        this.protocol = this.endpoint.getScheme().equals("https") ? "https" : "https,http";
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        this.clock = clock;
    }

    public String container() {
        return container;
    }

    /** Uploads (or replaces) a block blob in one request. */
    public void put(String blob, String contentType, String cacheControl, HttpRequest.BodyPublisher body) {
        HttpRequest.Builder request = HttpRequest.newBuilder(uri(blob, "cw", Duration.ofMinutes(30), null, null))
                .timeout(Duration.ofMinutes(10))
                .header("x-ms-version", VERSION)
                .header("x-ms-blob-type", "BlockBlob")
                .header("x-ms-blob-content-type", contentType)
                .PUT(body);
        if (cacheControl != null) request.header("x-ms-blob-cache-control", cacheControl);
        HttpResponse<String> response = send(request.build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 201) throw failure("upload", blob, response.statusCode(), response.body());
    }

    public void put(String blob, String contentType, String cacheControl, byte[] bytes) {
        put(blob, contentType, cacheControl, HttpRequest.BodyPublishers.ofByteArray(bytes));
    }

    public void put(String blob, String contentType, String cacheControl, Path file) {
        try {
            put(blob, contentType, cacheControl, HttpRequest.BodyPublishers.ofFile(file));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public Optional<Properties> head(String blob) {
        HttpRequest request = HttpRequest.newBuilder(uri(blob, "r", Duration.ofMinutes(5), null, null))
                .timeout(Duration.ofSeconds(30))
                .header("x-ms-version", VERSION)
                .method("HEAD", HttpRequest.BodyPublishers.noBody())
                .build();
        HttpResponse<Void> response = send(request, HttpResponse.BodyHandlers.discarding());
        if (response.statusCode() == 404) return Optional.empty();
        if (response.statusCode() != 200) throw failure("read", blob, response.statusCode(), "");
        OptionalLong length = response.headers().firstValueAsLong("Content-Length");
        String type = response.headers().firstValue("Content-Type").orElse("application/octet-stream");
        return Optional.of(new Properties(length.orElse(-1), type));
    }

    public InputStream get(String blob) {
        HttpRequest request = HttpRequest.newBuilder(uri(blob, "r", Duration.ofMinutes(30), null, null))
                .timeout(Duration.ofMinutes(10))
                .header("x-ms-version", VERSION)
                .GET()
                .build();
        HttpResponse<InputStream> response = send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() != 200) {
            try (InputStream ignored = response.body()) { /* drain */ } catch (IOException ignored) { }
            throw failure("read", blob, response.statusCode(), "");
        }
        return response.body();
    }

    public byte[] getBytes(String blob) {
        try (InputStream in = get(blob)) {
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void download(String blob, Path target) {
        HttpRequest request = HttpRequest.newBuilder(uri(blob, "r", Duration.ofMinutes(30), null, null))
                .timeout(Duration.ofMinutes(10))
                .header("x-ms-version", VERSION)
                .GET()
                .build();
        HttpResponse<Path> response = send(request, HttpResponse.BodyHandlers.ofFile(target));
        if (response.statusCode() != 200) throw failure("download", blob, response.statusCode(), "");
    }

    /** Deletes a blob; a missing blob is not an error. */
    public void delete(String blob) {
        HttpRequest request = HttpRequest.newBuilder(uri(blob, "d", Duration.ofMinutes(5), null, null))
                .timeout(Duration.ofSeconds(30))
                .header("x-ms-version", VERSION)
                .DELETE()
                .build();
        HttpResponse<String> response = send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 202 && response.statusCode() != 404) {
            throw failure("delete", blob, response.statusCode(), response.body());
        }
    }

    /** A read-only link that expires after {@code ttl}, optionally overriding the response headers. */
    public String readUrl(String blob, Duration ttl, String contentType, String contentDisposition) {
        return uri(blob, "r", ttl, contentType, contentDisposition).toString();
    }

    URI uri(String blob, String permissions, Duration ttl, String contentType, String contentDisposition) {
        return URI.create(endpoint + "/" + container + "/" + encodePath(blob) + "?" + sas(blob, permissions, ttl, contentType, contentDisposition));
    }

    /**
     * Service SAS for one blob (sr=b). String-to-sign layout for service versions 2020-12-06 and later:
     * permissions, start, expiry, canonical resource, identifier, IP, protocol, version, resource,
     * snapshot time, encryption scope, then the five response-header overrides.
     */
    String sas(String blob, String permissions, Duration ttl, String contentType, String contentDisposition) {
        Instant now = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        String start = SAS_TIME.format(now.minus(Duration.ofMinutes(5)));
        String expiry = SAS_TIME.format(now.plus(ttl));
        String canonical = "/blob/" + account + "/" + container + "/" + blob;
        String rsct = contentType == null ? "" : contentType;
        String rscd = contentDisposition == null ? "" : contentDisposition;
        String toSign = String.join("\n",
                permissions, start, expiry, canonical, "", "", protocol, VERSION, "b", "", "",
                "", rscd, "", "", rsct);
        StringBuilder query = new StringBuilder()
                .append("sv=").append(VERSION)
                .append("&spr=").append(encode(protocol))
                .append("&st=").append(encode(start))
                .append("&se=").append(encode(expiry))
                .append("&sr=b")
                .append("&sp=").append(permissions);
        if (!rscd.isEmpty()) query.append("&rscd=").append(encode(rscd));
        if (!rsct.isEmpty()) query.append("&rsct=").append(encode(rsct));
        query.append("&sig=").append(encode(sign(toSign)));
        return query.toString();
    }

    private String sign(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return Base64.getEncoder().encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Cannot sign Azure Blob request", e);
        }
    }

    private <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> handler) {
        try {
            return http.send(request, handler);
        } catch (IOException e) {
            throw new UncheckedIOException("Azure Blob request failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted during Azure Blob request", e);
        }
    }

    private IllegalStateException failure(String action, String blob, int status, String body) {
        String code = body == null ? "" : body.replaceAll("(?s).*<Code>([^<]*)</Code>.*", "$1");
        if (code.equals(body)) code = "";
        return new IllegalStateException("Azure Blob " + action + " of '" + blob + "' failed: HTTP " + status
                + (code.isEmpty() ? "" : " " + code));
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    /** Encodes each path segment, keeping the slashes between them. */
    static String encodePath(String blob) {
        String[] parts = blob.split("/", -1);
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) out.append('/');
            out.append(encode(parts[i]));
        }
        return out.toString();
    }
}
