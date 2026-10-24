package com.anshu.tokengate.usage;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class PricingServiceTest {

    private final PricingService pricing = new PricingService();

    @Test
    void calculatesCostUsingInputAndOutputPrices() {
        // claude-sonnet sample price: $3 input, $15 output per 1M tokens
        // (11 x 3 + 15 x 15) / 1,000,000 = 0.000258
        BigDecimal cost = pricing.calculateCost("claude-sonnet", 11, 15);
        assertThat(cost).isEqualByComparingTo("0.000258");
    }

    @Test
    void selfHostedModelIsFree() {
        assertThat(pricing.calculateCost("qwen2.5:1.5b", 500, 500)).isEqualByComparingTo("0");
    }

    @Test
    void unknownModelFallsBackToDefaultPrice() {
        // default: $1 input, $2 output per 1M tokens
        assertThat(pricing.calculateCost("some-new-model", 1_000_000, 1_000_000)).isEqualByComparingTo("3");
    }
}