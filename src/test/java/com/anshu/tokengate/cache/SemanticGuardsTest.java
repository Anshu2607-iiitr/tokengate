package com.anshu.tokengate.cache;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SemanticGuardsTest {

    // ---- Lab ki asli jodiyan ----

    @Test
    void sameMeaningPairsAreAllowed() {
        assertThat(SemanticGuards.isSafeMatch("What is India?", "India is what?")).isTrue();
        assertThat(SemanticGuards.isSafeMatch("heart attack????", "What is a heart attack?")).isTrue();
        assertThat(SemanticGuards.isSafeMatch("JoSAA round 2 date", "When does the second round of JoSAA start?")).isTrue();
    }

    @Test
    void blocksSafeVsUnsafe() {
        // Lab me similarity 0.961 thi (sabse zyada!). Guard ise rokta hai.
        assertThat(SemanticGuards.isSafeMatch("Is this medicine safe for kids?", "Is this medicine unsafe for kids?")).isFalse();
    }

    // ---- Negation words ----

    @Test
    void blocksNotAndContractions() {
        assertThat(SemanticGuards.isSafeMatch("Is it safe?", "Is it not safe?")).isFalse();
        assertThat(SemanticGuards.isSafeMatch("Can I eat this?", "Can't I eat this?")).isFalse();
    }

    @Test
    void sameNegationOnBothSidesIsFine() {
        assertThat(SemanticGuards.isSafeMatch("Why is my app not starting?", "My app is not starting, why?")).isTrue();
    }

    // ---- Numbers ----

    @Test
    void blocksDifferentNumbers() {
        assertThat(SemanticGuards.isSafeMatch("JoSAA round 2 date", "JoSAA round 3 date")).isFalse();
    }

    // ---- Honest limitation ----

    @Test
    void doesNotCatchDifferentCountries() {
        // Guard ye nahi pakadta. Yahan THRESHOLD bachata hai (lab me 0.762, threshold se neeche).
        assertThat(SemanticGuards.isSafeMatch("What is the capital of India?", "What is the capital of Pakistan?")).isTrue();
    }
}