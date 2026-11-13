package seekfactory.axoraa.services.serviceImpl;

import org.junit.jupiter.api.Test;
import seekfactory.axoraa.entity.Reels.Reel;
import seekfactory.axoraa.repository.ProductRepository;
import seekfactory.axoraa.repository.Reels.ReelRepository;
import seekfactory.axoraa.services.services.MediaStorageService;

import static org.mockito.Mockito.*;

class ProductVideoRetentionTest {
    @Test void deletingSeekKeepsVideoReferencedByProduct() {
        ReelRepository reels = mock(ReelRepository.class);
        ProductRepository products = mock(ProductRepository.class);
        MediaStorageService storage = mock(MediaStorageService.class);
        String video = "/api/v1/media/0b5e7d1c-2f4a-4c1e-9a3b-7d6e5f4a3b2c.mp4";
        Reel reel = Reel.builder().videoUrl(video).build();
        reel.setId("seek");
        when(products.existsByVideoUrl(video)).thenReturn(true);
        new ReelRemover(reels, storage, products).delete(reel);
        verify(reels).delete(reel);
        verifyNoInteractions(storage);
    }

    @Test void deletingSeekKeepsAbsoluteOrCdnProductAlias() {
        ReelRepository reels = mock(ReelRepository.class);
        ProductRepository products = mock(ProductRepository.class);
        MediaStorageService storage = mock(MediaStorageService.class);
        String key = "0b5e7d1c-2f4a-4c1e-9a3b-7d6e5f4a3b2c.mp4";
        Reel reel = Reel.builder().videoUrl("/api/v1/media/" + key).build();
        reel.setId("seek");
        when(products.existsByVideoUrlContaining(key)).thenReturn(true);
        new ReelRemover(reels, storage, products).delete(reel);
        verify(reels).delete(reel);
        verifyNoInteractions(storage);
    }

    @Test void unsharedSeekVideoStillDeletes() {
        ReelRepository reels = mock(ReelRepository.class);
        ProductRepository products = mock(ProductRepository.class);
        MediaStorageService storage = mock(MediaStorageService.class);
        String key = "0b5e7d1c-2f4a-4c1e-9a3b-7d6e5f4a3b2c.mp4";
        Reel reel = Reel.builder().videoUrl("/api/v1/media/" + key).build();
        reel.setId("seek");
        new ReelRemover(reels, storage, products).delete(reel);
        verify(storage).delete(key);
    }
}
