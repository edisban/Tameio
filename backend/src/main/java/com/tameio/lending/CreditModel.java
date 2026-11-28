package com.tameio.lending;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The credit model trained in ml/credit-risk: a logistic regression exported as its coefficients, so the
 * probability is worked out here with no other service to call. Each measure is capped, standardised and weighted;
 * its weighted value is how far it moves the estimate from an average applicant, which is what the analyst sees.
 *
 * <p>An estimate only: the scorecard stays the bank's score, and a person makes every decision (ADR 0005).
 */
@Component
class CreditModel {

    static final String RESOURCE = "lending/credit-model.json";
    private static final int PROBABILITY_DECIMALS = 4;
    private static final int CONTRIBUTION_DECIMALS = 4;

    /** The exported file, as written by credit_risk.train. */
    record Exported(String model, String version, String trainedOn, double intercept, List<Feature> features,
                    Map<String, Object> metrics) {
    }

    record Feature(String name, double cap, double mean, double scale, double coefficient) {
    }

    /** What the model says about one application. */
    record Estimate(BigDecimal probability, String modelVersion, List<CreditFactor> factors) {
    }

    /** The probability unrounded, with each measure's part in log-odds. */
    record Worked(double probability, List<Part> parts) {
    }

    record Part(String name, double value, double contribution) {
    }

    private final Exported exported;

    CreditModel(JsonMapper json) {
        this.exported = read(json);
    }

    Estimate estimate(CreditFeatures features) {
        Worked worked = work(features);
        List<CreditFactor> factors = worked.parts().stream()
            .map(p -> new CreditFactor(p.name(), BigDecimal.valueOf(p.value()),
                BigDecimal.valueOf(p.contribution()).setScale(CONTRIBUTION_DECIMALS, RoundingMode.HALF_EVEN)))
            .toList();
        return new Estimate(BigDecimal.valueOf(worked.probability()).setScale(PROBABILITY_DECIMALS, RoundingMode.HALF_EVEN),
            exported.version(), factors);
    }

    Worked work(CreditFeatures features) {
        double logit = exported.intercept();
        List<Part> parts = new ArrayList<>();
        for (Feature feature : exported.features()) {
            double value = Math.min(Math.max(features.values().get(feature.name()), 0), feature.cap());
            double contribution = feature.coefficient() * (value - feature.mean()) / feature.scale();
            logit += contribution;
            parts.add(new Part(feature.name(), value, contribution));
        }
        return new Worked(1 / (1 + Math.exp(-logit)), List.copyOf(parts));
    }

    private static Exported read(JsonMapper json) {
        try (InputStream in = new ClassPathResource(RESOURCE).getInputStream()) {
            return json.readValue(in, Exported.class);
        } catch (IOException e) {
            throw new IllegalStateException("The credit model " + RESOURCE + " could not be read", e);
        }
    }
}
