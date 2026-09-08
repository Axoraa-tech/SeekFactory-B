package seekfactory.axoraa.services.services;

import seekfactory.axoraa.dto.Request.user.UserUpdateRequest;
import seekfactory.axoraa.dto.Response.user.UserResponse;

public interface UserService {

    UserResponse getCurrentUser(String userId);

    UserResponse updateProfile(String userId, UserUpdateRequest request);

    void deactivateUser(String userId);
}