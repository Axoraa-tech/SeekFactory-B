package seekfactory.axoraa.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import seekfactory.axoraa.dto.Response.common.ApiResponse;
import seekfactory.axoraa.dto.Response.media.MediaUploadResponse;
import seekfactory.axoraa.services.services.MediaStorageService;

import java.util.concurrent.TimeUnit;

@RestController
@RequiredArgsConstructor
@Tag(name = "Media", description = "Seller media uploads (product photos, seek videos) and public serving")
public class MediaController {

    private final MediaStorageService mediaStorageService;

    @PostMapping(value = "/api/v1/factory/media", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('ROLE_SUPPLIER')")
    @Operation(summary = "Upload a product photo or seek video (multipart: file, kind=image|video)")
    public ResponseEntity<ApiResponse<MediaUploadResponse>> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam("kind") String kind) {
        MediaUploadResponse response = mediaStorageService.store(file, kind);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(response, "Media uploaded"));
    }

    /**
     * Public so buyers (and guests) can view product photos and play seeks.
     * Keys are random UUIDs and never reused, so responses can be cached indefinitely.
     */
    @GetMapping("/api/v1/media/{key}")
    @Operation(summary = "Serve an uploaded media file (supports HTTP Range for video seeking)")
    public ResponseEntity<Resource> serve(@PathVariable String key) {
        Resource resource = mediaStorageService.load(key);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(mediaStorageService.contentTypeOf(key)))
                .cacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic().immutable())
                .header("Content-Disposition", "inline")
                .body(resource);
    }
}
