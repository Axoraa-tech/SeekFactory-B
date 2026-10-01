package seekfactory.axoraa.services.serviceImpl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import seekfactory.axoraa.services.media.MediaTypes;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/**
 * One-time copy of an existing local {@code uploads/} folder into the bucket, for the switch from
 * {@code app.media.storage=local} to {@code s3}. Set {@code APP_MEDIA_MIGRATE_FROM_DIR=uploads},
 * start the server once, check the log, then remove the setting.
 *
 * Runs in the background after startup and skips objects that already exist, so it is safe to
 * re-run. Local files are left in place. Stored URLs ({@code /api/v1/media/<key>}) do not change.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.media.storage", havingValue = "s3")
public class MediaMigrationRunner {

    private static final String IMMUTABLE = "public, max-age=31536000, immutable";

    private final S3MediaStorageService storage;
    private final String fromDir;

    public MediaMigrationRunner(S3MediaStorageService storage,
                                @Value("${app.media.migrate-from-dir:}") String fromDir) {
        this.storage = storage;
        this.fromDir = fromDir;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        if (fromDir.isBlank()) return;
        Thread thread = new Thread(() -> migrate(Path.of(fromDir)), "media-migration");
        thread.setDaemon(true);
        thread.start();
    }

    /** @return files uploaded */
    int migrate(Path dir) {
        Path root = dir.toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) {
            log.warn("Media migration: {} is not a directory, nothing to copy", root);
            return 0;
        }
        log.info("Media migration: copying {} to the bucket", root);
        int[] counts = new int[3]; // uploaded, already there, failed

        // Public files: uploads/<uuid>.<ext> -> media/<uuid>.<ext>
        for (Path file : list(root)) {
            String name = file.getFileName().toString();
            if (MediaTypes.KEY_PATTERN.matcher(name).matches()) {
                copy(file, S3MediaStorageService.PUBLIC_PREFIX + name, IMMUTABLE, counts);
            }
        }
        // Videos whose compression never finished: publish the original under the final key
        Path pending = root.resolve("pending");
        if (Files.isDirectory(pending)) {
            for (Path file : list(pending)) {
                String name = file.getFileName().toString();
                String id = name.contains(".") ? name.substring(0, name.indexOf('.')) : name;
                String key = id + ".mp4";
                if (MediaTypes.KEY_PATTERN.matcher(key).matches() && !Files.isRegularFile(root.resolve(key))) {
                    copy(file, S3MediaStorageService.PUBLIC_PREFIX + key, "no-cache", counts);
                }
            }
        }
        // Chat attachments: uploads/private/<namespace>/<key> -> private/<namespace>/<key>
        Path privateRoot = root.resolve("private");
        if (Files.isDirectory(privateRoot)) {
            for (Path nsDir : list(privateRoot)) {
                String namespace = nsDir.getFileName().toString();
                if (!Files.isDirectory(nsDir) || !MediaTypes.NAMESPACE_PATTERN.matcher(namespace).matches()) continue;
                for (Path file : list(nsDir)) {
                    String name = file.getFileName().toString();
                    if (MediaTypes.PRIVATE_KEY_PATTERN.matcher(name).matches()) {
                        copy(file, S3MediaStorageService.privateObjectKey(namespace, name), null, counts);
                    }
                }
            }
        }
        log.info("Media migration finished: {} uploaded, {} already in the bucket, {} failed",
                counts[0], counts[1], counts[2]);
        return counts[0];
    }

    private void copy(Path file, String objectKey, String cacheControl, int[] counts) {
        try {
            if (storage.exists(objectKey)) {
                counts[1]++;
                return;
            }
            storage.putFile(objectKey, file, cacheControl);
            counts[0]++;
        } catch (RuntimeException e) {
            counts[2]++;
            log.warn("Media migration: could not upload {}: {}", file.getFileName(), e.getMessage());
        }
    }

    private static List<Path> list(Path dir) {
        try (Stream<Path> files = Files.list(dir)) {
            return files.filter(p -> Files.isRegularFile(p) || Files.isDirectory(p)).toList();
        } catch (IOException e) {
            log.warn("Media migration: cannot read {}: {}", dir, e.getMessage());
            return List.of();
        }
    }
}
