package seekfactory.axoraa.services.services;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;
import seekfactory.axoraa.dto.Response.media.MediaUploadResponse;

/**
 * Stores seller-uploaded product photos and seek videos.
 * Current implementation writes to local disk; swap for Aliyun OSS (+ VOD for video)
 * in production without changing controllers.
 */
public interface MediaStorageService {

    /**
     * Validate and persist an upload.
     *
     * @param kind "image" or "video"
     */
    MediaUploadResponse store(MultipartFile file, String kind);

    /** Resolve a stored media key for serving; throws ResourceNotFoundException if absent. */
    Resource load(String key);

    /** Content type to serve a stored key with (derived from its allowlisted extension). */
    String contentTypeOf(String key);
}
