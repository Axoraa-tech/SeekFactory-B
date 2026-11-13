package seekfactory.axoraa.services.serviceImpl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import seekfactory.axoraa.entity.Reels.Reel;
import seekfactory.axoraa.repository.Reels.ReelRepository;
import seekfactory.axoraa.repository.ProductRepository;
import seekfactory.axoraa.services.media.MediaTypes;
import seekfactory.axoraa.services.services.MediaStorageService;

/**
 * Hard-deletes a reel (seller delete and admin moderation share this path).
 *
 * Likes, saves, comments, hashtags and product tags go with it via ON DELETE CASCADE
 * (see V1__init_schema.sql); view_events have no FK and are left as history.
 * The uploaded video file is removed after commit unless another reel or product still uses it.
 */
@Component
@RequiredArgsConstructor
public class ReelRemover {

    private final ReelRepository reelRepository;
    private final MediaStorageService mediaStorageService;
    private final ProductRepository productRepository;

    public void delete(Reel reel) {
        String videoUrl = reel.getVideoUrl();
        boolean shared = videoUrl == null || reelRepository.existsByVideoUrlAndIdNot(videoUrl, reel.getId())
                || productRepository.existsByVideoUrl(videoUrl);
        if (!shared && videoUrl.startsWith(MediaTypes.PUBLIC_PATH)) {
            String key = videoUrl.substring(MediaTypes.PUBLIC_PATH.length());
            // Products may hold an absolute API/CDN URL or URL query rather than this relative URL.
            // Retaining an extra file on a conservative match is safer than breaking product playback.
            shared = MediaTypes.KEY_PATTERN.matcher(key).matches()
                    && productRepository.existsByVideoUrlContaining(key);
        }
        reelRepository.delete(reel);
        if (!shared && videoUrl.startsWith(MediaTypes.PUBLIC_PATH)) {
            // Remove the video file too, once the delete is committed. Thumbnails are left alone:
            // they are often product photos still in use.
            String key = videoUrl.substring(MediaTypes.PUBLIC_PATH.length());
            afterCommit(() -> mediaStorageService.delete(key));
        }
    }

    private static void afterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }
}
