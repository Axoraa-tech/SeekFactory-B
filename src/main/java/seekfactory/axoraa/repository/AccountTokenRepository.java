package seekfactory.axoraa.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import seekfactory.axoraa.entity.AccountToken;
import seekfactory.axoraa.enums.AccountTokenPurpose;

import java.util.List;
import java.util.Optional;

@Repository
public interface AccountTokenRepository extends JpaRepository<AccountToken, String> {

    Optional<AccountToken> findByTokenHashAndPurpose(String tokenHash, AccountTokenPurpose purpose);

    List<AccountToken> findByUserIdAndPurposeAndUsedAtIsNull(String userId, AccountTokenPurpose purpose);
}
