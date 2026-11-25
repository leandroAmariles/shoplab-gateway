package com.shoplab.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.shoplab.gateway.support.AbstractGatewayTest;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = {
        "shoplab.gateway.routes.eval.uri=http://127.0.0.1:1",
        // Boot turns exporters off in tests; this one is part of what is verified.
        "management.prometheus.metrics.export.enabled=true"})
class ManagementEndpointsTest extends AbstractGatewayTest {

    @Test
    void healthAndProbesAreUpEvenWithADestinationDown() {
        client.get().uri("/api/eval/settings").exchange().expectStatus().isEqualTo(503);

        for (String path : new String[] {"/actuator/health", "/actuator/health/liveness",
                "/actuator/health/readiness"}) {
            client.get().uri(path).exchange()
                    .expectStatus().isOk()
                    .expectBody().jsonPath("$.status").isEqualTo("UP");
        }
    }

    @Test
    void metricsAreExposedByRoute() {
        client.get().uri("/api/documents").exchange().expectStatus().isOk();

        String metrics = client.get().uri("/actuator/prometheus").exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .returnResult()
                .getResponseBody();

        assertThat(metrics).contains("spring_cloud_gateway_requests_seconds", "routeId=\"documents\"");
    }

    @Test
    void onlyHealthInfoAndPrometheusArePublished() {
        client.get().uri("/actuator/env").exchange().expectStatus().isNotFound();
        client.get().uri("/actuator/gateway/routes").exchange().expectStatus().isNotFound();

        assertThat(MONOLITH.received()).isEmpty();
    }
}
