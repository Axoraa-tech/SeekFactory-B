package seekfactory.axoraa.dto.Response.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Standard API error response envelope.
 *
 * {
 *   "success": false,
 *   "message": "Validation failed",
 *   "errors": ["Email is required", "Password must be at least 8 characters"],
 *   "status": 400,
 *   "timestamp": "2026-09-04T18:30:00Z"
 * }
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {

    @Builder.Default
    private boolean success = false;

    private String message;

    private List<String> errors;

    private int status;

    @Builder.Default
    private OffsetDateTime timestamp = OffsetDateTime.now();
}