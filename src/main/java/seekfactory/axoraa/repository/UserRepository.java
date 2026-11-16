package seekfactory.axoraa.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import seekfactory.axoraa.entity.User;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, String> {

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("SELECT u FROM User u WHERE u.id = :id")
    Optional<User> findForUpdate(String id);

    Optional<User> findByEmail(String email);

    Optional<User> findByPhone(String phone);

    // Read-only compatibility with formatted legacy numbers. Fetch no email/password data.
    interface PhoneCandidate {
        String getId();
        String getPhone();
        String getCountry();
    }

    @org.springframework.data.jpa.repository.Query(value = """
            SELECT id, phone, country FROM users
            WHERE phone IS NOT NULL
              AND regexp_replace(phone, '[^0-9]', '', 'g') LIKE CONCAT('%', :nationalNumber)
            """, nativeQuery = true)
    java.util.List<PhoneCandidate> findPhoneCandidates(String nationalNumber);

    Optional<User> findByGoogleId(String googleId);

    boolean existsByEmail(String email);

    /** Cheap per-request check used by the JWT filter (see ActiveUserCache). */
    boolean existsByIdAndIsActiveTrue(String id);
}
