package seekfactory.axoraa.services.media;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class VideoOptimizerKeyTest {
    private static final String ID = "9c052948-f232-4104-8de5-46dad6a5bf6c";

    @Test
    void includesExistingGalleryMovAndMp4Urls() {
        for (String extension : new String[]{"mov", "mp4"}) {
            var match = VideoOptimizer.KEY.matcher("https://example.com/api/v1/media/" + ID + "." + extension);
            assertThat(match.find()).isTrue();
            assertThat(match.group(1)).isEqualTo(ID + "." + extension);
        }
    }

    @Test
    void excludesOtherContainersAndInvalidKeys() {
        for (String url : new String[]{"/api/v1/media/" + ID + ".webm", "/api/v1/media/bad.mov", "/api/v1/media/" + ID + ".mov/extra"}) {
            assertThat(VideoOptimizer.KEY.matcher(url).find()).isFalse();
        }
    }
}
