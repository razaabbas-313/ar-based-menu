package com.atelier.media.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SlugPolicyTest {

    @Test
    void normalizesNamesIntoUrlSafeSlugs() {
        assertThat(SlugPolicy.normalize("Bistro Lumière")).isEqualTo("bistro-lumiere");
        assertThat(SlugPolicy.normalize("  Café & Bar  ")).isEqualTo("cafe-and-bar");
        assertThat(SlugPolicy.normalize("L'Atelier -- Paris!!")).isEqualTo("l-atelier-paris");
        assertThat(SlugPolicy.normalize(null)).isEmpty();
        assertThat(SlugPolicy.normalize("x".repeat(80))).hasSize(SlugPolicy.MAX_LENGTH);
    }

    @Test
    void reportsProblemsForUnusableSlugs() {
        assertThat(SlugPolicy.problem("bistro-lumiere")).isNull();
        assertThat(SlugPolicy.problem("ab")).isNotNull();
        assertThat(SlugPolicy.problem("-bad-")).isNotNull();
        assertThat(SlugPolicy.problem("Upper")).isNotNull();
        assertThat(SlugPolicy.problem("admin")).contains("reserved");
    }

    @Test
    void wellFormedCheckGuardsRoutes() {
        assertThat(SlugPolicy.isWellFormed("bistro-a")).isTrue();
        assertThat(SlugPolicy.isWellFormed("../etc/passwd")).isFalse();
        assertThat(SlugPolicy.isWellFormed("Bistro-A")).isFalse();
    }
}
