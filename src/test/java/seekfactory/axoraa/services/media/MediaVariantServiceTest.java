package seekfactory.axoraa.services.media;

import org.junit.jupiter.api.Test;
import seekfactory.axoraa.services.services.MediaStorageService;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class MediaVariantServiceTest {

    private static final String KEY = "0f8fad5b-d9cb-469f-a165-70867728950e.png";
    private static final String ID = "0f8fad5b-d9cb-469f-a165-70867728950e";

    /** A noisy (hard to compress) opaque photo-like PNG. */
    private static byte[] photoPng(int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Random random = new Random(7);
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) image.setRGB(x, y, random.nextInt(0xFFFFFF));
        Graphics2D g = image.createGraphics();
        g.setColor(Color.ORANGE);
        g.fillRect(width / 4, height / 4, width / 2, height / 2);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    /** In-memory storage that records variants. */
    private static MediaStorageService storage(byte[] original, Map<String, byte[]> variants) {
        MediaStorageService storage = mock(MediaStorageService.class);
        when(storage.readPublic(KEY)).thenReturn(original);
        when(storage.variantExists(anyString())).thenAnswer(inv -> variants.containsKey(inv.getArgument(0, String.class)));
        doAnswer(inv -> {
            variants.put(inv.getArgument(0), inv.getArgument(1));
            return null;
        }).when(storage).storeVariant(anyString(), any(), anyString());
        return storage;
    }

    @Test
    void makesASmallerJpegOnceAndReusesIt() throws IOException {
        Map<String, byte[]> variants = new HashMap<>();
        byte[] original = photoPng(1600, 1000);
        MediaStorageService storage = storage(original, variants);
        MediaVariantService service = new MediaVariantService(storage);

        Optional<String> first = service.variantFor(KEY, 640);
        assertThat(first).contains(ID + "-w640.jpg");
        byte[] copy = variants.get(ID + "-w640.jpg");
        assertThat(copy.length).isLessThan(original.length);
        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(copy));
        assertThat(Math.max(decoded.getWidth(), decoded.getHeight())).isEqualTo(640);

        // Second request: no new read or write
        assertThat(service.variantFor(KEY, 640)).contains(ID + "-w640.jpg");
        verify(storage, times(1)).readPublic(KEY);
        verify(storage, times(1)).storeVariant(anyString(), any(), anyString());
    }

    @Test
    void reusesACopyMadeByAnEarlierRun() throws IOException {
        Map<String, byte[]> variants = new HashMap<>(Map.of(ID + "-w1280.jpg", new byte[]{1}));
        MediaStorageService storage = storage(photoPng(10, 10), variants);

        assertThat(new MediaVariantService(storage).variantFor(KEY, 1280)).contains(ID + "-w1280.jpg");
        verify(storage, never()).readPublic(anyString());
    }

    @Test
    void servesTheOriginalWhenItIsAlreadySmall() throws IOException {
        Map<String, byte[]> variants = new HashMap<>();
        MediaStorageService storage = storage(photoPng(200, 120), variants);
        MediaVariantService service = new MediaVariantService(storage);

        // A JPEG copy of a tiny image may still be smaller; either way nothing larger is served
        Optional<String> result = service.variantFor(KEY, 640);
        result.ifPresent(name -> assertThat(variants.get(name).length).isLessThan(storage.readPublic(KEY).length));
    }

    @Test
    void failuresFallBackToTheOriginalAndAreRetriedLater() {
        MediaStorageService storage = mock(MediaStorageService.class);
        when(storage.readPublic(KEY)).thenThrow(new RuntimeException("bucket offline"));
        MediaVariantService service = new MediaVariantService(storage);

        assertThat(service.variantFor(KEY, 640)).isEmpty();
        assertThat(service.variantFor(KEY, 640)).isEmpty();
        verify(storage, times(2)).readPublic(KEY);
    }

    @Test
    void onlyKnownWidthsAndDecodablePhotos() {
        MediaVariantService service = new MediaVariantService(mock(MediaStorageService.class));
        assertThat(service.supports(KEY, 160)).isTrue();
        assertThat(service.supports(KEY, 640)).isTrue();
        assertThat(service.supports(KEY, 1280)).isTrue();
        assertThat(service.supports(KEY, 999)).isFalse();
        assertThat(service.supports(KEY, null)).isFalse();
        assertThat(service.supports(ID + ".webp", 640)).isFalse();
        assertThat(service.supports(ID + ".mp4", 640)).isFalse();
        assertThat(service.supports("../etc/passwd.png", 640)).isFalse();
    }
}
