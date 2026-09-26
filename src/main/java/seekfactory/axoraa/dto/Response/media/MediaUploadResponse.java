package seekfactory.axoraa.dto.Response.media;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Result of a factory media upload.
 * {@code url} is a server-relative path (e.g. /api/v1/media/abc.mp4) so stored
 * references survive a change of backend host; clients prefix it with the API origin.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MediaUploadResponse {

    private String url;
    private String contentType;
    private long size;
}
