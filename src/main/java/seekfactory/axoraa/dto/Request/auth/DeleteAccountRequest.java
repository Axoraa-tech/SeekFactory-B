package seekfactory.axoraa.dto.Request.auth;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Confirms a self-service account deletion. Accounts with a password must send it;
 * accounts without one (Google or phone sign-in) send {@code confirmation = "DELETE"}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeleteAccountRequest {

    @Size(max = 128)
    private String password;

    @Size(max = 32)
    private String confirmation;
}
