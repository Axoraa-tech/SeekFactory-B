package seekfactory.axoraa.config;

import jakarta.servlet.MultipartConfigElement;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.unit.DataSize;

/**
 * Multipart limits for seller media uploads (Spring's default is 1MB per file).
 * Defined here rather than in application.yaml; Boot's multipart auto-config
 * backs off when this bean exists. Per-kind limits are enforced in MediaStorageService.
 */
@Configuration
public class MediaConfig {

    @Bean
    public MultipartConfigElement multipartConfigElement(
            @Value("${app.media.max-video-size:100MB}") DataSize maxFileSize) {
        long maxFile = maxFileSize.toBytes();
        long maxRequest = maxFile + DataSize.ofMegabytes(1).toBytes(); // form fields + boundaries
        int spillToDiskThreshold = (int) DataSize.ofMegabytes(2).toBytes();
        return new MultipartConfigElement("", maxFile, maxRequest, spillToDiskThreshold);
    }
}
