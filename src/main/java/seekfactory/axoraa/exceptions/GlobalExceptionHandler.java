package seekfactory.axoraa.exceptions;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.catalina.connector.ClientAbortException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;

import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import seekfactory.axoraa.dto.Response.common.ErrorResponse;
import seekfactory.axoraa.services.services.ErrorLogService;

import java.util.List;

/**
 * Global exception handler that converts all exceptions into consistent
 * ErrorResponse JSON objects. This ensures the frontend always receives
 * a predictable error format regardless of what went wrong.
 *
 * Priority order: specific handlers first, then generic fallback.
 */

@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final ErrorLogService errorLogService;

    /**
     * The viewer scrolled past or closed a seek while its video was streaming. Normal for media, not
     * an error: nothing to log or store, and no body can be written to a response that is gone.
     */
    @ExceptionHandler({AsyncRequestNotUsableException.class, ClientAbortException.class})
    public void handleClientAbort(Exception ex, HttpServletRequest request) {
        log.debug("Client closed the connection on {} {}", request.getMethod(), request.getRequestURI());
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException ex) {
        return buildResponse(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    /**
     * Unknown paths (scanners, typos, disabled API docs) are a plain 404, not an "unhandled"
     * 500: they must not be logged as crashes or written to error_logs on every probe.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResource(NoResourceFoundException ex) {
        return buildResponse(HttpStatus.NOT_FOUND, "Not found");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotAllowed(HttpRequestMethodNotSupportedException ex) {
        return buildResponse(HttpStatus.METHOD_NOT_ALLOWED, "Method not allowed");
    }

    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<ErrorResponse> handleConflict(DuplicateResourceException ex) {
        return buildResponse(HttpStatus.CONFLICT, ex.getMessage());
    }

    /** A unique key hit by two simultaneous clicks (like, save, follow, cart): the first one won. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataConflict(DataIntegrityViolationException ex) {
        log.warn("Data integrity conflict: {}", ex.getMostSpecificCause().getMessage());
        return buildResponse(HttpStatus.CONFLICT, "This change conflicts with existing data. Please refresh and try again.");
    }

    @ExceptionHandler({UnauthorizedException.class, TokenExpiredException.class})
    public ResponseEntity<ErrorResponse> handleUnauthorized(RuntimeException ex) {
        return buildResponse(HttpStatus.UNAUTHORIZED, ex.getMessage());
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ErrorResponse> handleForbidden(ForbiddenException ex) {
        return buildResponse(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    @ExceptionHandler({BadRequestException.class, IllegalArgumentException.class})
    public ResponseEntity<ErrorResponse> handleBadRequest(RuntimeException ex) {
        return buildResponse(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    /** Malformed JSON, unknown enum values or missing required primitives in a request body. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableBody(HttpMessageNotReadableException ex) {
        return buildResponse(HttpStatus.BAD_REQUEST, "Request body is missing, malformed or contains invalid values");
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleUploadTooLarge(MaxUploadSizeExceededException ex) {
        return buildResponse(HttpStatus.CONTENT_TOO_LARGE, "Upload exceeds the maximum allowed size");
    }

    @ExceptionHandler({MultipartException.class, MissingServletRequestPartException.class,
            MissingServletRequestParameterException.class})
    public ResponseEntity<ErrorResponse> handleBadMultipart(Exception ex) {
        return buildResponse(HttpStatus.BAD_REQUEST, "Invalid upload: send multipart form fields 'file' and 'kind'");
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex) {
        return buildResponse(HttpStatus.FORBIDDEN, "You do not have permission to perform this action");
    }

    /**
     * Handles @Valid validation failures.
     * Extracts field-level error messages and returns them as a list.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        List<String> errors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(FieldError::getDefaultMessage)
                .toList();

        ErrorResponse response = ErrorResponse.builder()
                .message("Validation failed")
                .errors(errors)
                .status(HttpStatus.BAD_REQUEST.value())
                .build();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    /**
     * Catch-all handler for any unhandled exceptions.
     * Logs the error and returns a generic 500 response.
     */

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneral(Exception ex, HttpServletRequest request) {
        log.error("💥 Unhandled exception on {} {}: ", request.getMethod(), request.getRequestURI(), ex);

        // Persist to error_logs table
        errorLogService.logError(ex, request, HttpStatus.INTERNAL_SERVER_ERROR.value());
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred. Please try again later.");
    }

    // ─── Helper ───────────────────────────────────────────────
    private ResponseEntity<ErrorResponse> buildResponse(HttpStatus status, String message) {
        ErrorResponse response = ErrorResponse.builder()
                .message(message)
                .status(status.value())
                .build();
        return ResponseEntity.status(status).body(response);
    }
}