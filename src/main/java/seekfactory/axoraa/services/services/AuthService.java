package seekfactory.axoraa.services.services;

import seekfactory.axoraa.dto.Request.auth.*;
import seekfactory.axoraa.dto.Response.auth.AuthResponse;

public interface AuthService {

    AuthResponse register(RegisterRequest request);

    AuthResponse login(LoginRequest request);

    AuthResponse loginWithPhone(PhoneLoginRequest request);

    AuthResponse loginWithGoogle(GoogleAuthRequest request);

    AuthResponse refreshToken(RefreshTokenRequest request);

    void logout(String userId);
}
