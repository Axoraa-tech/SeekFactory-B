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
    /** Decoding a large PNG holds its pixels in memory: a page of new photos waits its turn. */
    private final Semaphore generating = new Semaphore(2);
    /** "<key>@<width>" -> variant name, or NO_VARIANT when the original is already as small. */
    private final Map<String, String> known = new ConcurrentHashMap<>();

    public boolean supports(String key, Integer width) {
        return width != null && WIDTHS.contains(width) && key != null && SOURCE_KEY.matcher(key).matches();
    }

    /**
     * The variant to serve for a photo at a width, generating it if needed. Empty means "serve
     * the original" (already small enough, not decodable, or generation failed).
     */
    public Optional<String> variantFor(String key, int width) {
        String memo = key + "@" + width;
        String cached = known.get(memo);
        if (cached != null) return cached.isEmpty() ? Optional.empty() : Optional.of(cached);

        var m = SOURCE_KEY.matcher(key);
        if (!m.matches()) return Optional.empty();
        String id = m.group(1);
        String extension = m.group(2);

        Optional<String> existing = findStored(id, width);
        if (existing.isPresent()) return remember(memo, existing.get());

        try {
            generating.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
        try {
            // Another request may have made it while this one waited
            existing = findStored(id, width);
            if (existing.isPresent()) return remember(memo, existing.get());

            byte[] original = storage.readPublic(key);
            Optional<ImageCompressor.Result> result = new ImageCompressor(width, QUALITY).compress(original, extension);
            if (result.isEmpty()) return remember(memo, NO_VARIANT);
            String name = variantName(id, width, result.get().extension());
            storage.storeVariant(name, result.get().bytes(), MediaTypes.contentTypeOf(name));
            return remember(memo, name);
        } catch (RuntimeException e) {
            // Missing original, storage hiccup: serve the original this time and try again later
            log.warn("Could not make a {}px copy of {}: {}", width, key, e.getMessage());
            return Optional.empty();
        } finally {
            generating.release();
        }
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
