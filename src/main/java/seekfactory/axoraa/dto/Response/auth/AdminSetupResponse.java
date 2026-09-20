package seekfactory.axoraa.dto.Response.auth;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminSetupResponse {
    private String secret;
    private String qrCodeUri;
}
