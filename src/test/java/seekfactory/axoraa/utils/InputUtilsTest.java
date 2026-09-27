package seekfactory.axoraa.utils;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InputUtilsTest {

    @Test
    void leadingIntReadsQuantitiesFromFreeText() {
        assertThat(InputUtils.leadingInt("500 pcs", 1)).isEqualTo(500);
        assertThat(InputUtils.leadingInt("1,000 units", 1)).isEqualTo(1000);
        assertThat(InputUtils.leadingInt("  12", 1)).isEqualTo(12);
        assertThat(InputUtils.leadingInt("1 piece", 7)).isEqualTo(1);
    }

    @Test
    void leadingIntFallsBackWhenThereIsNoNumber() {
        assertThat(InputUtils.leadingInt(null, 1)).isEqualTo(1);
        assertThat(InputUtils.leadingInt("Negotiable", 1)).isEqualTo(1);
        assertThat(InputUtils.leadingInt("0 pcs", 5)).isEqualTo(5);
        assertThat(InputUtils.leadingInt("99999999999", 3)).isEqualTo(3);
    }

    @Test
    void likePatternMatchesAllForBlankAndStripsWildcards() {
        assertThat(InputUtils.likePattern(null)).isEqualTo("%");
        assertThat(InputUtils.likePattern("   ")).isEqualTo("%");
        assertThat(InputUtils.likePattern("%_")).isEqualTo("%");
        assertThat(InputUtils.likePattern(" CNC Lathe ")).isEqualTo("%cnc lathe%");
        assertThat(InputUtils.likePattern("50%_off")).isEqualTo("%50off%");
    }

    @Test
    void isSafeUrlAllowsHttpAndServerPathsOnly() {
        assertThat(InputUtils.isSafeUrl(null)).isTrue();
        assertThat(InputUtils.isSafeUrl("https://cdn.example.com/a.png")).isTrue();
        assertThat(InputUtils.isSafeUrl("/api/v1/media/abc.pdf")).isTrue();
        assertThat(InputUtils.isSafeUrl("javascript:alert(1)")).isFalse();
        assertThat(InputUtils.isSafeUrl("data:text/html;base64,xx")).isFalse();
        assertThat(InputUtils.isSafeUrl("//evil.example.com/x")).isFalse();
    }
}
