package seekfactory.axoraa.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Component

@Order(Ordered.HIGHEST_PRECEDENCE)
public class ApiLoggingFilter extends OncePerRequestFilter {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** Bodies carry personal data (names, phones, addresses, chats); prod turns this off. */
    @Value("${app.logging.request-bodies:true}")
    private boolean logBodies;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        ContentCachingRequestWrapper reqWrapper = new ContentCachingRequestWrapper(request, 1024 * 1024);
        ContentCachingResponseWrapper resWrapper = new ContentCachingResponseWrapper(response);

        long startTime = System.currentTimeMillis();

        try {
            filterChain.doFilter(reqWrapper, resWrapper);
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            logApiDetails(reqWrapper, resWrapper, duration);
            resWrapper.copyBodyToResponse();
        }
    }

    private void logApiDetails(ContentCachingRequestWrapper req, ContentCachingResponseWrapper res, long duration) {
        String method = req.getMethod();
        String uri = req.getRequestURI();
        String queryString = req.getQueryString() != null ? "?" + req.getQueryString() : "";
        int status = res.getStatus();

        // Auth calls carry passwords, OTPs and tokens: never write their bodies to the log.
        // Production skips bodies entirely (app.logging.request-bodies=false).
        boolean sensitive = !logBodies || uri.contains("/auth/");
        String reqBody = sensitive ? "" : formatJson(getPayload(req.getContentAsByteArray()));
        String resBody = sensitive ? "" : formatJson(getPayload(res.getContentAsByteArray()));

        StringBuilder sb = new StringBuilder();
        sb.append("\n========================= [API CALL START] =========================");
        sb.append(String.format("\n👉 HTTP:     %s %s%s", method, uri, queryString));
        sb.append(String.format("\n⏱️ TIME:     %d ms", duration));
        sb.append(String.format("\n📡 STATUS:   %d", status));

        if (!reqBody.isEmpty()) {
            sb.append("\n📥 REQUEST BODY:\n").append(reqBody);
        }

        if (!resBody.isEmpty()) {
            sb.append("\n📤 RESPONSE BODY:\n").append(resBody);
        }
        sb.append("\n========================== [API CALL END] ==========================\n");

        if (status >= 400) {
            log.warn(sb.toString());
        } else {
            log.info(sb.toString());
        }
    }

    private String getPayload(byte[] buf) {
        if (buf == null || buf.length == 0) return "";
        try {
            return new String(buf, StandardCharsets.UTF_8).trim();
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * Converts single-line JSON string into indented, multi-line pretty JSON.
     */
    private String formatJson(String rawJson) {
        if (rawJson == null || rawJson.isBlank()) return "";
        try {
            Object jsonObject = redact(objectMapper.readValue(rawJson, Object.class));
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(jsonObject);
        } catch (Exception e) {
            // Not JSON: never echo arbitrary bodies that might carry credentials
            return rawJson.length() > 500 ? "[non-JSON body, " + rawJson.length() + " chars omitted]" : rawJson;
        }
    }

    /** Keys whose values must never reach the logs (credentials, tokens, secrets). */
    private static final Set<String> SENSITIVE_KEYS = Set.of(
            "password", "newpassword", "currentpassword", "passwordhash",
            "accesstoken", "access_token", "refreshtoken", "refresh_token", "token",
            "idtoken", "id_token", "credential", "secret", "totpsecret", "otp", "code");

    @SuppressWarnings("unchecked")
    private Object redact(Object node) {
        if (node instanceof Map<?, ?> map) {
            Map<String, Object> copy = new LinkedHashMap<>();
            ((Map<String, Object>) map).forEach((key, value) ->
                    copy.put(key, SENSITIVE_KEYS.contains(key.toLowerCase()) ? "***" : redact(value)));
            return copy;
        }
        if (node instanceof List<?> list) {
            return list.stream().map(this::redact).toList();
        }
        return node;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        String accept = request.getHeader("Accept");
        String contentType = request.getContentType();
        return path.startsWith("/swagger-ui") || path.startsWith("/api-docs") || path.startsWith("/actuator")
                // Response caching would swallow Server-Sent Events after the first flush
                || path.endsWith("/stream")
                || (accept != null && accept.contains("text/event-stream"))
                // Binary uploads/downloads: don't buffer files in memory or dump them into logs
                || path.startsWith("/api/v1/media/")
                || path.contains("/attachments")
                || (contentType != null && contentType.startsWith("multipart/"));
    }
}