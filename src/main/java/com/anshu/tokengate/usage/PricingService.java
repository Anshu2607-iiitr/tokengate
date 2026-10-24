package com.anshu.tokengate.usage;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

@Service
public class PricingService {

    // SAMPLE prices: USD per 1 million tokens (input, output).
    // Real prices badalte rehte hain; production me ye config/DB me hone chahiye.
    private static final Map<String, ModelPrice> PRICES = Map.of(
            "gpt-4o-mini", new ModelPrice(new BigDecimal("0.15"), new BigDecimal("0.60")),
            "gpt-4o", new ModelPrice(new BigDecimal("2.50"), new BigDecimal("10.00")),
            "claude-sonnet", new ModelPrice(new BigDecimal("3.00"), new BigDecimal("15.00")),
            "gemini-flash", new ModelPrice(new BigDecimal("0.10"), new BigDecimal("0.40"))
    );

    private static final ModelPrice DEFAULT_PRICE =
            new ModelPrice(new BigDecimal("1.00"), new BigDecimal("2.00"));

    private static final BigDecimal ONE_MILLION = new BigDecimal("1000000");

    public BigDecimal calculateCost(String model, int promptTokens, int completionTokens) {
        ModelPrice price = PRICES.getOrDefault(model, DEFAULT_PRICE);
        BigDecimal inputCost = price.inputPerMillion().multiply(BigDecimal.valueOf(promptTokens));
        BigDecimal outputCost = price.outputPerMillion().multiply(BigDecimal.valueOf(completionTokens));
        return inputCost.add(outputCost).divide(ONE_MILLION, 10, RoundingMode.HALF_UP);
    }

    public record ModelPrice(BigDecimal inputPerMillion, BigDecimal outputPerMillion) {}
}