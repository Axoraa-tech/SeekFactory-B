package seekfactory.axoraa.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Fails the production start-up when a setting would leave the app open.
 *
 * application.yaml keeps a development fallback for the JWT signing key, which is public in the
 * repository; tokens signed with it can be forged by anyone. In prod the key must come from
 * JWT_SECRET and be long enough for HMAC-SHA256. The mock OTP must also be off.
 *
 * Runs as a BeanFactoryPostProcessor so it fires before any bean is created: nothing (database
 * connection, Flyway migrations, web server) starts with an unsafe configuration.
 */
@Slf4j
@Component
@Profile("prod")
public class ProductionSafetyCheck implements BeanFactoryPostProcessor, EnvironmentAware {

    /** HMAC-SHA256 needs at least a 256-bit key. */
    static final int MIN_SECRET_BYTES = 32;

    private Environment environment;

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
        List<String> problems = problems(environment.getProperty("JWT_SECRET"), environment.getProperty("app.auth.mock-otp"));
        // The root invitation token is public; its reset bypass would hand out the admin account
        if ("true".equalsIgnoreCase(environment.getProperty("app.admin.allow-root-reset"))) {
            problems.add("app.admin.allow-root-reset is enabled; the public root admin token must never work in production");
        }
        if (!problems.isEmpty()) {
            throw new IllegalStateException("Refusing to start in production:\n - " + String.join("\n - ", problems));
        }
        log.info("Production safety check passed");
    }

    static List<String> problems(String jwtSecret, String mockOtp) {
        List<String> problems = new ArrayList<>();
        if (jwtSecret == null || jwtSecret.isBlank()) {
            problems.add("JWT_SECRET is not set, so tokens would be signed with the public development key");
        } else if (jwtSecret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            problems.add("JWT_SECRET must be at least " + MIN_SECRET_BYTES + " bytes of random data");
        }
        if (mockOtp != null && !mockOtp.isBlank()) {
            problems.add("app.auth.mock-otp is set; the mock phone code must never be enabled in production");
        }
        return problems;
    }
}
