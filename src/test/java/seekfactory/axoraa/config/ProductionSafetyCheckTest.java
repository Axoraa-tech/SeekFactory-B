package seekfactory.axoraa.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProductionSafetyCheckTest {

    private static final String STRONG = "k7Q2vX9pL4mN8rT1wY6zB3cF5hJ0sD2gA8eU4iO7";

    @Test
    void passesWithStrongSecretAndNoMockOtp() {
        assertThat(ProductionSafetyCheck.problems(STRONG, "")).isEmpty();
        assertThat(ProductionSafetyCheck.problems(STRONG, null)).isEmpty();
    }

    @Test
    void failsWhenSecretMissing() {
        assertThat(ProductionSafetyCheck.problems(null, "")).singleElement().asString().contains("JWT_SECRET is not set");
        assertThat(ProductionSafetyCheck.problems("  ", "")).singleElement().asString().contains("JWT_SECRET is not set");
    }

    @Test
    void failsWhenSecretTooShort() {
        assertThat(ProductionSafetyCheck.problems("short-secret", "")).singleElement().asString().contains("at least 32 bytes");
    }

    @Test
    void failsWhenMockOtpEnabled() {
        assertThat(ProductionSafetyCheck.problems(STRONG, "123456")).singleElement().asString().contains("mock-otp");
    }

    @Test
    void reportsEveryProblemAtOnce() {
        assertThat(ProductionSafetyCheck.problems(null, "123456")).hasSize(2);
    }
}
