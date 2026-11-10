package com.anshu.tokengate.cache;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PromptNormalizerTest {

    @Test
    void lowercasesText() {
        assertThat(PromptNormalizer.normalize("What is India")).isEqualTo("what is india");
    }

    @Test
    void collapsesAndTrimsSpaces() {
        assertThat(PromptNormalizer.normalize("   what    is   india  ")).isEqualTo("what is india");
    }

    @Test
    void removesTrailingPunctuation() {
        assertThat(PromptNormalizer.normalize("WHAT IS INDIA???")).isEqualTo("what is india");
        assertThat(PromptNormalizer.normalize("what is india ?")).isEqualTo("what is india");
    }

    @Test
    void keepsPunctuationInTheMiddle() {
        assertThat(PromptNormalizer.normalize("What is India? Tell me more.")).isEqualTo("what is india? tell me more");
    }

    @Test
    void differentVariantsGiveSameResult() {
        String a = PromptNormalizer.normalize("What is India?");
        String b = PromptNormalizer.normalize("  what is   india ");
        String c = PromptNormalizer.normalize("WHAT IS INDIA!!");
        assertThat(a).isEqualTo(b).isEqualTo(c);
    }

    @Test
    void wordOrderIsNotFixedByNormalization() {
        // Ye semantic cache ka kaam hai (Lesson 2+), normalization ka nahi
        assertThat(PromptNormalizer.normalize("India is what?"))
                .isNotEqualTo(PromptNormalizer.normalize("What is India?"));
    }

    @Test
    void handlesNullAndBlank() {
        assertThat(PromptNormalizer.normalize(null)).isEmpty();
        assertThat(PromptNormalizer.normalize("   ")).isEmpty();
    }
}