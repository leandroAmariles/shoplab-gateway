package com.shoplab.gateway.routing;

import static org.assertj.core.api.Assertions.assertThat;

import com.shoplab.gateway.support.AbstractGatewayTest;
import com.shoplab.gateway.support.RawHttp;
import java.io.IOException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Rules R1-R6 of specs/LAB-107 contracts/routing.md. */
class RoutingContractTest extends AbstractGatewayTest {

    @ParameterizedTest
    @ValueSource(strings = {"/api/chat", "/api/documents", "/api/knowledge", "/api/eval", "/api/assistant"})
    void r1TheExactPrefixGoesToItsDestination(String path) {
        client.get().uri(path).exchange().expectStatus().isOk().expectBody(String.class).isEqualTo("monolith");

        assertThat(MONOLITH.received()).extracting("uri").containsExactly(path);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/chat/feedback/42", "/api/documents/7", "/api/knowledge/search",
            "/api/eval/history/runs", "/api/assistant/settings"})
    void r2ASubPathGoesToItsDestination(String path) {
        client.get().uri(path).exchange().expectStatus().isOk();

        assertThat(MONOLITH.received()).extracting("uri").containsExactly(path);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            // R3: only looks like a prefix
            "/api/chatbot", "/api/documents-old",
            // R4: services that do not exist yet
            "/api/catalog", "/api/orders/1", "/api/tickets",
            // R5: outside the published API
            "/", "/api", "/actuator/env", "/v3/api-docs"})
    void r3ToR5APathWithoutRouteIsNotFound(String path) {
        client.get().uri(path).exchange()
                .expectStatus().isNotFound()
                .expectBody().jsonPath("$.code").isEqualTo("SLHD_GATE_00001");

        assertThat(MONOLITH.received()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/chat/../../actuator/env", "/api/chat/%2e%2e/%2e%2e/actuator/env",
            "/api/chat/..%2f..%2factuator/env", "/api/chat/%2E./actuator", "/api/chat/./feedback",
            "/api/chat/..;x=1/actuator", "/api/chat/..%5c..%5cactuator"})
    void r6DotSegmentsNeverReachADestination(String path) throws IOException {
        String response = RawHttp.get(port, path);

        assertThat(response).doesNotContain("monolith");
        assertThat(response).matches("(?s)HTTP/1\\.1 (400|404).*");
        assertThat(MONOLITH.received()).isEmpty();
    }
}
