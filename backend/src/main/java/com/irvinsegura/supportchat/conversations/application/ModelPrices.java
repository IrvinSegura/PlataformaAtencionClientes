package com.irvinsegura.supportchat.conversations.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.Optional;

/**
 * List prices of the language models, in US dollars per million tokens, to estimate what an answer cost. An
 * estimate for the business's eyes only: the provider's invoice is the truth, and models missing here have no
 * estimate rather than a wrong one.
 */
public record ModelPrices(Map<String, Price> perModel) {

    private static final BigDecimal MILLION = BigDecimal.valueOf(1_000_000);

    public ModelPrices {
        perModel = perModel == null ? Map.of() : Map.copyOf(perModel);
    }

    public Optional<BigDecimal> cost(String model, long inputTokens, long outputTokens) {
        Price price = model == null ? null : perModel.get(model);
        if (price == null) {
            return Optional.empty();
        }
        BigDecimal cost = price.inputPerMillion().multiply(BigDecimal.valueOf(inputTokens))
                .add(price.outputPerMillion().multiply(BigDecimal.valueOf(outputTokens)))
                .divide(MILLION, 6, RoundingMode.HALF_UP);
        return Optional.of(cost);
    }

    public record Price(BigDecimal inputPerMillion, BigDecimal outputPerMillion) {
    }
}
