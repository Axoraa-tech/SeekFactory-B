package seekfactory.axoraa.services.media;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import seekfactory.axoraa.services.services.MediaStorageService;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Smaller copies of public photos for lists and cards ({@code /api/v1/media/<key>?w=160|640|1280}).
 * Originals are kept up to 2048 px (often multi-MB PNGs); a card shows ~400 px, so it loads a
 * 640 px copy instead (1280 px for large views). A variant is made the first time it is asked
 * for, stored next to the originals and reused from then on; see {@link MediaVariantWarmer} to
 * make them all ahead of time.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MediaVariantService {

    /** Logo / avatar size, card / list size, and large view size (longest side, px). */
    public static final Set<Integer> WIDTHS = Set.of(160, 640, 1280);
    private static final float QUALITY = 0.8f;
    /** Only photos ImageIO can decode; WebP/GIF/AVIF are already small or animated. */
    private static final Pattern SOURCE_KEY = Pattern.compile(
            "^([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})\\.(png|jpg)$");
    /** Variant names are built here only; anything else is never looked up. */
    private static final String NO_VARIANT = "";

    private final MediaStorageService storage;
    /** Bound concurrent decodes and how long an HTTP request waits for a decode slot. */
    private final Semaphore generating = new Semaphore(2);
    static final long GENERATION_WAIT_MS = 250;
    /** "<key>@<width>" -> variant name, or NO_VARIANT when the original is already as small. */
    private final Map<String, String> known = new ConcurrentHashMap<>();
    /**
     * Photos whose copy could not be made (original missing, storage error), with when that happened.
     * Without it every view of such a photo re-checked storage (several blob lookups) and retried.
     */
    private final Map<String, Long> recentFailures = new ConcurrentHashMap<>();
    static final long RETRY_FAILED_AFTER_MS = 5 * 60_000;
    /** Replaceable in tests. */
    java.util.function.LongSupplier clock = System::currentTimeMillis;

    public boolean supports(String key, Integer width) {
        return width != null && WIDTHS.contains(width) && key != null && SOURCE_KEY.matcher(key).matches();
    }

    /**
     * The variant to serve for a photo at a width, generating it if needed. Empty means "serve
     * the original" (already small enough, not decodable, or generation failed).
     */
    public Optional<String> variantFor(String key, int width) {
        // Validate here too: warmers and other callers need the same limits as the controller.
        if (!supports(key, width)) return Optional.empty();
        String memo = key + "@" + width;
        String cached = known.get(memo);
        if (cached != null) return cached.isEmpty() ? Optional.empty() : Optional.of(cached);

        var m = SOURCE_KEY.matcher(key);
        if (!m.matches()) return Optional.empty();
        Long failedAt = recentFailures.get(memo);
        if (failedAt != null) {
            if (clock.getAsLong() - failedAt < RETRY_FAILED_AFTER_MS) return Optional.empty();
            recentFailures.remove(memo);
        }
        String id = m.group(1);
        String extension = m.group(2);

        boolean acquired = false;
        try {
            // Storage failures here must also use the normal original-file fallback/backoff.
            Optional<String> existing = findStored(id, width);
            if (existing.isPresent()) return remember(memo, existing.get());

            acquired = generating.tryAcquire(GENERATION_WAIT_MS, TimeUnit.MILLISECONDS);
            if (!acquired) return Optional.empty();
            // A waiting request may now have a positive OR negative in-memory answer.
            cached = known.get(memo);
            if (cached != null) return cached.isEmpty() ? Optional.empty() : Optional.of(cached);
            existing = findStored(id, width);
            if (existing.isPresent()) return remember(memo, existing.get());

            byte[] original = storage.readPublic(key);
            Optional<ImageCompressor.Result> result = new ImageCompressor(width, QUALITY).compress(original, extension);
            if (result.isEmpty()) return remember(memo, NO_VARIANT);
            String name = variantName(id, width, result.get().extension());
            storage.storeVariant(name, result.get().bytes(), MediaTypes.contentTypeOf(name));
            return remember(memo, name);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (RuntimeException e) {
            // Missing original, storage hiccup: serve the original this time and try again later
            log.warn("Could not make a {}px copy of {}: {}", width, key, e.getMessage());
            if (recentFailures.size() > 20_000) recentFailures.clear();
            recentFailures.put(memo, clock.getAsLong());
            return Optional.empty();
        } finally {
            if (acquired) generating.release();
        }
    }

    /**
     * Makes sure {@code variants/<uuid>-w<width>.<original extension>} exists, so a browser can load the
     * copy straight from public storage by a name it can work out itself, with no request to this API
     * (each one is an extra round trip from mainland China). The file holds the smaller copy when there
     * is one, else the original (already small, or not decodable); its content type tells the browser
     * which. Returns false when it could not be made (the browser then falls back to the API address).
     */
    public boolean ensureDirectCopy(String key, int width) {
        if (!supports(key, width)) return false;
        var m = SOURCE_KEY.matcher(key);
        if (!m.matches()) return false;
        String name = variantName(m.group(1), width, m.group(2));
        try {
            if (storage.variantExists(name)) return true;
            if (!generating.tryAcquire(30, TimeUnit.SECONDS)) return false;
            try {
                if (storage.variantExists(name)) return true;
                byte[] original = storage.readPublic(key);
                Optional<ImageCompressor.Result> result = new ImageCompressor(width, QUALITY).compress(original, m.group(2));
                if (result.isPresent()) {
                    storage.storeVariant(name, result.get().bytes(), MediaTypes.contentTypeOf("x." + result.get().extension()));
                } else {
                    storage.storeVariant(name, original, MediaTypes.contentTypeOf(key));
                }
                return true;
            } finally {
                generating.release();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (RuntimeException e) {
            log.warn("Could not make the direct {}px copy of {}: {}", width, key, e.getMessage());
            return false;
        }
    }

    /** Direct copies at every width, off the request thread (after an upload). */
    public void ensureDirectCopiesLater(String key) {
        if (key == null || !SOURCE_KEY.matcher(key).matches()) return;
        Thread.ofVirtual().name("direct-copies").start(() -> {
            for (int width : WIDTHS) ensureDirectCopy(key, width);
        });
    }

    private Optional<String> findStored(String id, int width) {
        for (String extension : List.of("jpg", "png")) {
            String name = variantName(id, width, extension);
            if (storage.variantExists(name)) return Optional.of(name);
        }
        return Optional.empty();
    }

    private Optional<String> remember(String memo, String name) {
        if (known.size() > 50_000) known.clear();  // bounded: one short string per photo and width
        known.put(memo, name);
        return name.isEmpty() ? Optional.empty() : Optional.of(name);
    }

    static String variantName(String id, int width, String extension) {
        return id + "-w" + width + "." + extension;
    }
}
