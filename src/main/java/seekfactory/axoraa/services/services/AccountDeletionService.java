package seekfactory.axoraa.services.services;

/** Self-service account deletion. */
public interface AccountDeletionService {

    /** The literal an account without a password types to confirm deletion. */
    String CONFIRMATION_WORD = "DELETE";

    /** Display name that replaces a deleted user's name in chats, orders and RFQs. */
    String DELETED_NAME = "Deleted user";

    /**
     * Checks the password (or the confirmation word for accounts without one), then anonymises
     * the account. The user row stays so conversations, orders and RFQs remain readable for the
     * other party, now showing "Deleted user".
     */
    void deleteAccount(String userId, String password, String confirmation);
}
