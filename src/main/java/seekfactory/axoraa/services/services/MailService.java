package seekfactory.axoraa.services.services;

/** Outbound transactional email (account links). */
public interface MailService {

    /** Sends a plain-text email. Never throws: delivery problems are logged. */
    void send(String to, String subject, String body);
}
