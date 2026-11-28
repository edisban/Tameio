package com.tameio.lending;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * The model in Java against the Python package that trained it: the examples in credit-golden.json were written by
 * {@code python -m credit_risk.golden}, and each measure, contribution and probability here has to match them.
 */
class CreditModelTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final double SAME = 1e-6;
    private static final CreditModel MODEL = new CreditModel(JSON);

    static List<JsonNode> cases() throws IOException {
        try (InputStream in = new ClassPathResource("lending/credit-golden.json").getInputStream()) {
            JsonNode golden = JSON.readTree(in);
            return StreamSupport.stream(golden.get("cases").spliterator(), false).toList();
        }
    }

    private static CreditFeatures measured(JsonNode example) {
        List<CreditFeatures.Month> months = new ArrayList<>();
        for (int i = 0; i < example.get("inflow").size(); i++) {
            months.add(new CreditFeatures.Month(example.get("inflow").get(i).asDouble(), example.get("outflow").get(i).asDouble()));
        }
        return CreditFeatures.of(example.get("amount").asDouble(), example.get("instalment").asDouble(),
            example.get("balance").asDouble(), example.get("historyMonths").asInt(), months);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void javaMeasuresAnApplicationAsPythonDoes(JsonNode example) {
        CreditFeatures features = measured(example);

        example.get("expected").get("measures").properties().forEach(expected ->
            assertThat(features.values().get(expected.getKey())).as(expected.getKey())
                .isCloseTo(expected.getValue().asDouble(), within(SAME)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void javaGivesTheProbabilityAndContributionsPythonGives(JsonNode example) {
        CreditModel.Worked worked = MODEL.work(measured(example));

        assertThat(worked.probability()).isCloseTo(example.get("expected").get("probability").asDouble(), within(SAME));
        worked.parts().forEach(part -> assertThat(part.contribution()).as(part.name())
            .isCloseTo(example.get("expected").get("contributions").get(part.name()).asDouble(), within(SAME)));
    }

    @Test
    void aSteadyWellCoveredBusinessIsEstimatedSaferThanOneInTheRed() throws IOException {
        List<JsonNode> examples = cases();
        double steady = MODEL.work(measured(examples.getFirst())).probability();
        double inTheRed = MODEL.work(measured(examples.get(2))).probability();

        assertThat(steady).isLessThan(inTheRed);
    }

    @Test
    void theEstimateIsRoundedForTheCaseAndCarriesItsModelVersion() throws IOException {
        CreditModel.Estimate estimate = MODEL.estimate(measured(cases().getFirst()));

        assertThat(estimate.probability().scale()).isEqualTo(4);
        assertThat(estimate.modelVersion()).isEqualTo("1");
        assertThat(estimate.factors()).extracting(CreditFactor::measure).containsExactly("repayment_ratio", "loan_size",
            "history_months", "cash_cushion", "inflow_volatility", "negative_months");
    }
}
