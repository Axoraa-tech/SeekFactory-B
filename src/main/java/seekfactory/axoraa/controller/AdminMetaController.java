package seekfactory.axoraa.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import seekfactory.axoraa.dto.Response.common.ApiResponse;
import seekfactory.axoraa.dto.Response.settings.FeedShowcaseSettings;
import seekfactory.axoraa.enums.RfqStatus;
import seekfactory.axoraa.enums.UserRole;
import seekfactory.axoraa.enums.VerificationStatus;

import java.util.Arrays;
import java.util.List;

/**
 * Option lists for admin clients, read from the backend enums so apps never hardcode them.
 * Plan payments share the PENDING / APPROVED / REJECTED lifecycle of manufacturer verification.
 */
@RestController
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
@Tag(name = "Admin", description = "Platform administration (ROLE_ADMIN only)")
public class AdminMetaController {

    public record AdminMeta(
            List<String> roles,
            List<String> rfqStatuses,
            List<String> verificationStatuses,
            List<String> paymentStatuses,
            List<String> showcaseModes,
            List<Integer> analyticsPeriods) {}

    @GetMapping("/api/v1/admin/meta")
    @Operation(summary = "Enum option lists used by the admin consoles")
    public ResponseEntity<ApiResponse<AdminMeta>> meta() {
        List<String> verification = names(VerificationStatus.values());
        return ResponseEntity.ok(ApiResponse.of(new AdminMeta(
                Arrays.stream(UserRole.values()).map(r -> r.name().replace("ROLE_", "")).toList(),
                names(RfqStatus.values()),
                verification,
                verification,
                names(FeedShowcaseSettings.Mode.values()),
                List.of(7, 30, 90, 365))));
    }

    private static List<String> names(Enum<?>[] values) {
        return Arrays.stream(values).map(Enum::name).toList();
    }
}
