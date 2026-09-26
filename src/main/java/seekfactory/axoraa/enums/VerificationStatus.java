package seekfactory.axoraa.enums;


/**
 * Admin review state of a manufacturer account.
 *
 * A manufacturer is only visible to buyers once APPROVED; PENDING and REJECTED
 * factories can sign in and prepare their catalogue but do not appear in the
 * feed, explore or search.
 */


public enum VerificationStatus {
    PENDING,    // Registered, awaiting admin review
    APPROVED,   // Reviewed and accepted — visible to buyers
    REJECTED    // Reviewed and declined, with a reason on the record
}
