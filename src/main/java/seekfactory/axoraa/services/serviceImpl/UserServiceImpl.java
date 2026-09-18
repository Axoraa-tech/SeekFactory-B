package seekfactory.axoraa.services.serviceImpl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import seekfactory.axoraa.dto.Request.user.UserUpdateRequest;
import seekfactory.axoraa.dto.Response.user.UserResponse;
import seekfactory.axoraa.entity.User;
import seekfactory.axoraa.exceptions.ResourceNotFoundException;
import seekfactory.axoraa.repository.UserRepository;
import seekfactory.axoraa.services.services.UserService;

/**
 * Manages user profile operations.
 * All profile updates are performed on the currently authenticated user only.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final ModelMapper modelMapper;

    @Override
    @Transactional(readOnly = true)
    public UserResponse getCurrentUser(String userId) {
        User user = findUserOrThrow(userId);
        return mapToResponse(user);
    }

    @Override
    public UserResponse updateProfile(String userId, UserUpdateRequest request) {
        User user = findUserOrThrow(userId);

        // Update only non-null fields (partial update pattern)
        if (request.getName() != null) {
            user.setName(request.getName());
        }
        if (request.getCompanyName() != null) {
            user.setCompanyName(request.getCompanyName());
        }
        if (request.getIndustry() != null) {
            user.setIndustry(request.getIndustry());
        }
        if (request.getCountry() != null) {
            user.setCountry(request.getCountry());
        }
        if (request.getPhone() != null) {
            user.setPhone(request.getPhone());
        }
        if (request.getAvatarUrl() != null) {
            user.setAvatarUrl(request.getAvatarUrl());
        }

        User saved = userRepository.save(user);
        log.info("User profile updated: {}", saved.getId());
        return mapToResponse(saved);
    }

    @Override
    public void deactivateUser(String userId) {
        User user = findUserOrThrow(userId);
        user.setIsActive(false);
        userRepository.save(user);
        log.info("User deactivated: {}", userId);
    }

    // ─── Private Helpers ──────────────────────────────────────

    private User findUserOrThrow(String userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
    }

    private UserResponse mapToResponse(User user) {
        UserResponse response = modelMapper.map(user, UserResponse.class);
        // Map enum to frontend-friendly string
        response.setRole(switch (user.getRole()) {
            case ROLE_BUYER -> "Buyer";
            case ROLE_SUPPLIER -> "Supplier";
            case ROLE_ADMIN -> "Admin";
        });
        return response;
    }
}