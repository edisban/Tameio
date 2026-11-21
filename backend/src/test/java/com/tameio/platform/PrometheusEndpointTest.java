package com.tameio.platform;

import com.tameio.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import static org.assertj.core.api.Assertions.assertThat;

/** What Prometheus scrapes from each instance. Tests turn metrics export off by default; this one turns it on. */
@SpringBootTest
@AutoConfigureMetrics
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PrometheusEndpointTest {

    private final MockMvcTester mvc;

    PrometheusEndpointTest(MockMvcTester mvc) {
        this.mvc = mvc;
    }

    @Test
    void prometheusReadsTheBusinessFiguresWithoutSigningIn() {
        MvcTestResult scraped = mvc.get().uri("/actuator/prometheus").exchange();

        assertThat(scraped).hasStatusOk();
        assertThat(scraped).bodyText()
            .contains("tameio_documents{")
            .contains("tameio_documents_stuck_reading")
            .contains("tameio_loan_applications{")
            .contains("tameio_loans_disbursed_euros")
            .contains("tameio_loan_instalments{")
            .contains("hikaricp_connections_active");
    }

    @Test
    void everyRequestIsTimedInBucketsSoThe95thPercentileCanBeWorkedOut() {
        mvc.get().uri("/actuator/health").exchange();

        assertThat(mvc.get().uri("/actuator/prometheus").exchange()).bodyText().contains("http_server_requests_seconds_bucket{");
    }
}
