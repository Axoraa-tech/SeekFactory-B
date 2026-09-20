package seekfactory.axoraa.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import seekfactory.axoraa.entity.AdminInvitation;

import java.util.Optional;

@Repository
public interface AdminInvitationRepository extends JpaRepository<AdminInvitation, String> {
    Optional<AdminInvitation> findByToken(String token);
    boolean existsByEmailAndIsUsedFalse(String email);
}
