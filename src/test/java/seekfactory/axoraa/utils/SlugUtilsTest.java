package seekfactory.axoraa.utils;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class SlugUtilsTest {

    @Test
    void keepsReadableNameAndAddsRandomSuffix() {
        String slug = SlugUtils.uniqueSlug("5-Axis CNC Center (VMC 1200)", "product");
        assertThat(slug).matches("5-axis-cnc-center-vmc-1200-[a-z0-9]{8}");
    }

    @Test
    void chineseNameFallsBack() {
        assertThat(SlugUtils.uniqueSlug("东莞精密模具", "product")).matches("product-[a-z0-9]{8}");
        assertThat(SlugUtils.uniqueSlug(null, "product")).matches("product-[a-z0-9]{8}");
    }

    @Test
    void longNamesFitTheColumn() {
        String slug = SlugUtils.uniqueSlug("very long machine name ".repeat(20), "product");
        assertThat(slug.length()).isLessThanOrEqualTo(128);
        assertThat(slug).doesNotContain("--");
    }

    @Test
    void sameNameGivesDifferentSlugs() {
        Set<String> slugs = new HashSet<>();
        for (int i = 0; i < 2000; i++) slugs.add(SlugUtils.uniqueSlug("Hydraulic Press", "product"));
        assertThat(slugs).hasSize(2000);
    }
}
