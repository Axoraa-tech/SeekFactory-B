package seekfactory.axoraa.services.serviceImpl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import seekfactory.axoraa.services.services.MailService;

/**
 * Sends through SMTP when configured via environment variables
 * (SPRING_MAIL_HOST, SPRING_MAIL_PORT, SPRING_MAIL_USERNAME, SPRING_MAIL_PASSWORD, APP_MAIL_FROM).
 *
 * Without SMTP the message is not delivered. Under the dev profile its body (with the
 * link) is logged so reset and verification can be tested locally; other profiles never
 * log it, because the links grant account access. Production must configure SMTP.
 */
@Slf4j
@Service
public class SmtpMailService implements MailService {

    private final ObjectProvider<JavaMailSender> mailSender;
    private final String from;
    private final boolean devProfile;

    public SmtpMailService(ObjectProvider<JavaMailSender> mailSender,
                           Environment environment,
                           @Value("${app.mail.from:SeekFactory <no-reply@seekfactory.com>}") String from) {
        this.mailSender = mailSender;
        this.from = from;
        this.devProfile = environment.acceptsProfiles(Profiles.of("dev"));
    }

    @Override
    public void send(String to, String subject, String body) {
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null) {
            if (devProfile) {
                log.warn("SMTP not configured (set SPRING_MAIL_HOST); email to {} not sent.\nSubject: {}\n{}", to, subject, body);
            } else {
                log.error("SMTP not configured (set SPRING_MAIL_HOST); email '{}' to {} was not sent", subject, to);
            }
            return;
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            sender.send(message);
        } catch (Exception e) {
            log.error("Failed to send email '{}' to {}: {}", subject, to, e.getMessage());
        }
    }
}
