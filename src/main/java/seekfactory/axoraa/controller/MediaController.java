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
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import seekfactory.axoraa.dto.Response.common.ApiResponse;
import seekfactory.axoraa.dto.Response.media.MediaUploadResponse;
import seekfactory.axoraa.exceptions.BadRequestException;
import seekfactory.axoraa.services.services.MediaStorageService;
import seekfactory.axoraa.exceptions.ResourceNotFoundException;
import seekfactory.axoraa.services.media.HlsPaths;
import seekfactory.axoraa.services.media.MediaTypes;
import seekfactory.axoraa.services.media.MediaVariantService;
import seekfactory.axoraa.utils.JwtTokenProvider;

import java.net.URI;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@RestController
@RequiredArgsConstructor
@Tag(name = "Media", description = "Seller media uploads (product photos, seek videos) and public serving")
public class MediaController {

    private final MediaStorageService mediaStorageService;
    private final JwtTokenProvider jwtTokenProvider;
    private final MediaVariantService variantService;

    @PostMapping(value = "/api/v1/factory/media", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('ROLE_SUPPLIER')")
    @Operation(summary = "Upload a product photo or seek video (multipart: file, kind=image|video|document)")
    public ResponseEntity<ApiResponse<MediaUploadResponse>> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam("kind") String kind) {
        MediaUploadResponse response = mediaStorageService.store(file, kind);
        prepareDirectCopies(kind, response);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(response, "Media uploaded"));
    }

    /**
     * Any signed-in user (buyers included) can upload images, e.g. a profile photo, and
     * documents such as drawings attached to RFQs and chat messages. Seek videos stay seller-only.
     */
    @PostMapping(value = "/api/v1/media", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload an image or document (multipart: file, kind=image|document)")
    public ResponseEntity<ApiResponse<MediaUploadResponse>> uploadForUser(
            @RequestParam("file") MultipartFile file,
            @RequestParam("kind") String kind) {
        if (!"image".equals(kind) && !"document".equals(kind)) {
            throw new BadRequestException("kind must be 'image' or 'document'");
        }
        MediaUploadResponse response = mediaStorageService.store(file, kind);
        prepareDirectCopies(kind, response);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(response, "File uploaded"));
    }

    /** New photos get their small copies right away, so browsers can load them straight from storage. */
    private void prepareDirectCopies(String kind, MediaUploadResponse response) {
        if (!"image".equals(kind) || response == null || response.getUrl() == null) return;
        String url = response.getUrl();
        variantService.ensureDirectCopiesLater(url.substring(url.lastIndexOf('/') + 1));
    }

    /**
     * Issues a 5-minute pass for one of the upload endpoints above. The web app sends files
     * straight here with it instead of through its proxy, which caps request bodies at 4.5 MB.
     */
    @PostMapping("/api/v1/media/upload-token")
    @Operation(summary = "Get a short-lived token for a direct media upload")
    public ResponseEntity<ApiResponse<Map<String, Object>>> uploadToken(Authentication authentication) {
        String role = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .findFirst()
                .orElseThrow(() -> new BadRequestException("No role on this session"));
        String token = jwtTokenProvider.generateUploadToken(authentication.getName(), role);
        Map<String, Object> body = Map.of(
                "token", token,
                "expiresIn", JwtTokenProvider.UPLOAD_TOKEN_EXPIRY_MS / 1000);
        return ResponseEntity.ok(ApiResponse.of(body, "Upload token issued"));
    }

    /**
     * Public so buyers (and guests) can view product photos and play seeks.
     * Keys are random UUIDs and never reused, so responses can be cached indefinitely.
     */
    @GetMapping("/api/v1/media/{key}")
    @Operation(summary = "Serve an uploaded media file (supports HTTP Range for video seeking); "
            + "?w=160|640|1280 serves a smaller copy of a photo")
    public ResponseEntity<Resource> serve(@PathVariable String key,
                                          @RequestParam(name = "w", required = false) Integer width) {
        // Lists and cards ask for a small copy; anything not resizable falls through to the original
        if (variantService.supports(key, width)) {
            Optional<ResponseEntity<Resource>> variant = variantService.variantFor(key, width).flatMap(this::serveVariant);
            if (variant.isPresent()) return variant.get();
        }
        // Bucket storage: send the browser to the CDN (old links stored before the switch keep working)
        Optional<String> location = mediaStorageService.publicUrl(key);
        if (location.isPresent()) {
            String type = MediaTypes.contentTypeOf(key);
            long maxAge = (type.startsWith("image/") || type.startsWith("video/"))
                    && !mediaStorageService.isProcessing(key)
                    ? mediaStorageService.publicRedirectMaxAgeSeconds() : 0;
            return ResponseEntity.status(HttpStatus.FOUND)
                    .location(URI.create(location.get()))
                    .cacheControl(redirectCache(maxAge))
                    .build();
        }
        Resource resource = mediaStorageService.load(key);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(mediaStorageService.contentTypeOf(key)))
                // A video still being compressed serves its original under the final key: don't let
                // browsers or CDNs keep that copy
                .cacheControl(mediaStorageService.isProcessing(key)
                        ? CacheControl.noCache()
                        : CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic().immutable())
                .header("Content-Disposition", "inline")
                .header("X-Content-Type-Options", "nosniff")
                .body(resource);
    }

    /**
     * Adaptive-streaming files of a seek video ({@code master.m3u8}, quality playlists, segments). Players
     * resolve the playlists' relative segment names against the address they loaded, so these are only
     * redirected to permanent links; with signed links (which a relative name would lose) they are streamed.
     */
    @GetMapping("/api/v1/media/hls/{id}/{file}")
    @Operation(summary = "Adaptive-streaming (HLS) file of a seek video")
    public ResponseEntity<Resource> getHls(@PathVariable String id, @PathVariable String file) {
        if (!HlsPaths.isValid(id, file)) throw new ResourceNotFoundException("Media", "file", file);
        String path = HlsPaths.path(id, file);
        CacheControl cache = HlsPaths.isImmutable(file)
                ? CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic().immutable()
                : CacheControl.maxAge(1, TimeUnit.HOURS).cachePublic();
        if (mediaStorageService.publicLinksAreStable()) {
            Optional<String> location = mediaStorageService.publicFileUrl(path);
            if (location.isPresent()) {
                return ResponseEntity.status(HttpStatus.FOUND)
                        .location(URI.create(location.get()))
                        .cacheControl(CacheControl.maxAge(1, TimeUnit.DAYS).cachePublic())
                        .build();
            }
        }
        Resource resource = mediaStorageService.loadPublicFile(path)
                .orElseThrow(() -> new ResourceNotFoundException("Media", "file", file));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(HlsPaths.contentTypeOf(file)))
                .cacheControl(cache)
                .header("X-Content-Type-Options", "nosniff")
                .body(resource);
    }

    /**
     * Redirects to a signed link stay in the viewer's own cache only, and only for part of the link's life.
     * A permanent address (public container or CDN) never changes, so any cache may keep the redirect.
     */
    private CacheControl redirectCache(long maxAge) {
        if (maxAge <= 0) return CacheControl.noCache();
        CacheControl cache = CacheControl.maxAge(maxAge, TimeUnit.SECONDS);
        return mediaStorageService.publicLinksAreStable() ? cache.cachePublic() : cache.cachePrivate().mustRevalidate();
    }

    /** A stored copy: redirect to the CDN when storage has one, otherwise stream it (never changes). */
    private Optional<ResponseEntity<Resource>> serveVariant(String name) {
        Optional<String> location = mediaStorageService.variantUrl(name);
        if (location.isPresent()) {
            long maxAge = mediaStorageService.publicRedirectMaxAgeSeconds();
            return Optional.of(ResponseEntity.status(HttpStatus.FOUND)
                    .location(URI.create(location.get()))
                    .cacheControl(redirectCache(maxAge))
                    .build());
        }
        return mediaStorageService.loadVariant(name).map(resource -> ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(MediaTypes.contentTypeOf(name)))
                .cacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic().immutable())
                .header("X-Content-Type-Options", "nosniff")
                .body(resource));
    }
}
