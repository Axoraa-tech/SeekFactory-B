package seekfactory.axoraa.services.serviceImpl;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import seekfactory.axoraa.config.ActiveUserCache;
import seekfactory.axoraa.entity.User;
import seekfactory.axoraa.enums.UserRole;
import seekfactory.axoraa.exceptions.BadRequestException;
import seekfactory.axoraa.exceptions.ResourceNotFoundException;
import seekfactory.axoraa.repository.UserRepository;
import seekfactory.axoraa.services.services.AccountDeletionService;
import seekfactory.axoraa.services.services.PresenceService;

import java.time.Instant;

/**
 * Deletes an account by anonymising it rather than removing the row.
 *
 * Every foreign key to users is ON DELETE CASCADE, so deleting the row would also delete the
 * conversations, messages, orders, RFQs and factory listings that other users still rely on.
 * Instead:
 * <ul>
 *   <li>the user row loses every personal field, gets a unique placeholder email (so the real
 *       address can sign up again), is deactivated and stamped with deleted_at;</li>
 *   <li>purely personal rows go: likes, saves, follows, cart, notifications, account tokens;
 *       plan payment proofs and payer details are wiped (the amount stays for the books);</li>
 *   <li>conversations, messages, orders and RFQs stay and now show "Deleted user"; open orders
 *       and RFQs are cancelled so the other side does not keep waiting;</li>
 *   <li>a manufacturer's factory, products and seeks are unlisted so buyers no longer find them,
 *       while existing chats and orders stay readable.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class AccountDeletionServiceImpl implements AccountDeletionService {

    private static final String OPEN_ORDER_STATUSES = "('PENDING', 'CONTACTED', 'NEGOTIATING')";
    private static final String OPEN_RFQ_STATUSES = "('SUBMITTED', 'REVIEWING', 'QUOTING', 'QUOTED')";
    /** Personal rows that only matter to their owner. */
    private static final String[] PERSONAL_TABLES = {"reel_likes", "reel_saves", "comment_likes",
            "product_likes", "product_saves", "manufacturer_follows", "cart_items", "notifications",
            "account_tokens"};

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final PresenceService presenceService;
    private final ActiveUserCache activeUserCache;

    @PersistenceContext
    private EntityManager em;

    @Override
    public void deleteAccount(String userId, String password, String confirmation) {
        User user = userRepository.findForUpdate(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
        if (user.getDeletedAt() != null || !Boolean.TRUE.equals(user.getIsActive())) {
            throw new BadRequestException("This account has already been deleted or deactivated");
        }
        if (user.getRole() == UserRole.ROLE_ADMIN) {
            throw new BadRequestException("Admin accounts are removed by another admin, not from here");
        }
        confirm(user, password, confirmation);

        Instant now = Instant.now();
        removePersonalRows(userId);
        scrubPlanPayments(userId);
        anonymiseSharedRecords(userId);
        if (user.getRole() == UserRole.ROLE_SUPPLIER) {
            unlistFactory(userId);
        }
        anonymiseUser(user, now);

        presenceService.markOffline(userId);
        activeUserCache.evict(userId);
        log.info("Account {} deleted (anonymised)", userId);
    }

    void confirm(User user, String password, String confirmation) {
        if (user.getPasswordHash() != null) {
            if (password == null || password.isEmpty()
                    || !passwordEncoder.matches(password, user.getPasswordHash())) {
                throw new BadRequestException("Password is incorrect");
            }
        } else if (confirmation == null || !CONFIRMATION_WORD.equalsIgnoreCase(confirmation.trim())) {
            throw new BadRequestException("Type " + CONFIRMATION_WORD + " to confirm deleting your account");
        }
    }

    /** Likes, saves, follows, cart, notifications and email-link tokens, keeping public counters right. */
    private void removePersonalRows(String userId) {
        exec("UPDATE reels SET likes_count = GREATEST(likes_count - 1, 0) "
                + "WHERE id IN (SELECT reel_id FROM reel_likes WHERE user_id = :uid)", userId);
        exec("UPDATE reels SET saves_count = GREATEST(saves_count - 1, 0) "
                + "WHERE id IN (SELECT reel_id FROM reel_saves WHERE user_id = :uid)", userId);
        exec("UPDATE comments SET likes_count = GREATEST(likes_count - 1, 0) "
                + "WHERE id IN (SELECT comment_id FROM comment_likes WHERE user_id = :uid)", userId);
        exec("UPDATE manufacturers SET follower_count = GREATEST(follower_count - 1, 0) "
                + "WHERE id IN (SELECT manufacturer_id FROM manufacturer_follows WHERE user_id = :uid)", userId);
        for (String table : PERSONAL_TABLES) {
            exec("DELETE FROM " + table + " WHERE user_id = :uid", userId);
        }
    }

    /** The uploaded proof and payer details go; the amount and decision stay for the books. */
    private void scrubPlanPayments(String userId) {
        exec("""
                UPDATE plan_payments SET
                    proof_data = decode('', 'hex'), proof_size = 0, proof_filename = NULL,
                    payer_reference = NULL, payer_name = NULL, payer_email = NULL, payer_phone = NULL,
                    payer_company = NULL, payer_country = NULL, payer_address = NULL,
                    rejection_reason = CASE WHEN status = 'PENDING' THEN 'Account deleted' ELSE rejection_reason END,
                    reviewed_at = CASE WHEN status = 'PENDING' THEN CURRENT_TIMESTAMP ELSE reviewed_at END,
                    status = CASE WHEN status = 'PENDING' THEN 'REJECTED' ELSE status END
                WHERE user_id = :uid
                """, userId);
    }

    /** Records the other party still sees: keep them, drop the personal details, close what is open. */
    private void anonymiseSharedRecords(String userId) {
        // Comments copy the author's details at posting time
        exec("UPDATE comments SET author_name = '" + DELETED_NAME + "', author_avatar_url = NULL, "
                + "author_company = NULL, author_country = NULL, is_verified = FALSE WHERE user_id = :uid", userId);
        // Orders placed as a buyer: open ones are cancelled, the delivery contact is personal data
        exec("UPDATE order_requests SET status = 'CANCELLED', cancel_reason = 'Buyer deleted their account', "
                + "status_updated_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP "
                + "WHERE buyer_id = :uid AND status IN " + OPEN_ORDER_STATUSES, userId);
        exec("UPDATE order_requests SET contact_name = NULL, contact_phone = NULL, delivery_address = NULL "
                + "WHERE buyer_id = :uid", userId);
        // RFQs copy the company name; open ones are withdrawn
        exec("UPDATE rfqs SET company_name = '" + DELETED_NAME + "', "
                + "status = CASE WHEN status IN " + OPEN_RFQ_STATUSES + " THEN 'CANCELLED' ELSE status END "
                + "WHERE user_id = :uid", userId);
    }

    /**
     * Hides the factory, its products and seeks from buyers. The factory name stays on the row so
     * a buyer's existing chats and orders with it remain recognisable.
     */
    private void unlistFactory(String userId) {
        String mfr = "(SELECT id FROM manufacturers WHERE user_id = :uid)";
        exec("UPDATE products SET listed = FALSE WHERE manufacturer_id IN " + mfr, userId);
        exec("UPDATE reels SET listed = FALSE WHERE manufacturer_id IN " + mfr, userId);
        exec("UPDATE rfq_quotes SET status = 'EXPIRED' WHERE status = 'PENDING' AND manufacturer_id IN " + mfr, userId);
        exec("UPDATE order_requests SET status = 'CANCELLED', cancel_reason = 'Seller deleted their account', "
                + "status_updated_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP "
                + "WHERE status IN " + OPEN_ORDER_STATUSES + " AND manufacturer_id IN " + mfr, userId);
        // verified = false removes it from feed, explore, search and its public profile;
        // REJECTED keeps it out of the admin approval queue
        exec("""
                UPDATE manufacturers SET verified = FALSE, premium = FALSE, verification_status = 'REJECTED',
                    rejection_reason = 'Account deleted by its owner', reviewed_at = CURRENT_TIMESTAMP,
                    chairman_name = NULL
                WHERE user_id = :uid
                """, userId);
    }

    private void anonymiseUser(User user, Instant now) {
        user.setName(DELETED_NAME);
        user.setEmail(placeholderEmail(user.getId()));
        user.setPhone(null);
        user.setGoogleId(null);
        user.setPasswordHash(null);
        user.setAvatarUrl(null);
        user.setCompanyName(null);
        user.setIndustry(null);
        user.setCountry(null);
        user.setTaxId(null);
        user.setAddress(null);
        user.setTotpSecret(null);
        user.setIsTotpEnabled(false);
        user.setEmailVerified(false);
        user.setIsActive(false);
        user.setDeletedAt(now);
        userRepository.save(user);
    }

    /** Unique per account and never deliverable (.invalid), so the real address is free to sign up again. */
    static String placeholderEmail(String userId) {
        return "deleted-" + userId + "@deleted.seekfactory.invalid";
    }

    private void exec(String sql, String userId) {
        em.createNativeQuery(sql).setParameter("uid", userId).executeUpdate();
    }
}
