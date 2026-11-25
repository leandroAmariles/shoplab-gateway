package com.shoplab.gateway.shared.error;

import static org.assertj.core.api.Assertions.assertThat;

import com.shoplab.gateway.support.AbstractGatewayTest;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

/** Errors born in the gateway: contracts/routing.md of specs/LAB-107. */
@TestPropertySource(properties = {
        // Nothing listens on port 1.
        "shoplab.gateway.routes.eval.uri=http://127.0.0.1:1",
        "shoplab.gateway.routes.assistant.uri=http://does-not-exist.invalid:8080",
        "shoplab.gateway.routes.knowledge.response-timeout=300ms"})
class GatewayErrorTest extends AbstractGatewayTest {

    private void expectError(WebTestClient.ResponseSpec response, int status, String code) {
        byte[] body = response.expectStatus().isEqualTo(status)
                .expectHeader().contentTypeCompatibleWith("application/json")
                .expectBody()
                // The shape of ApiErrorResponse in shoplab-contracts: code, message and a nullable detail.
                .jsonPath("$.code").isEqualTo(code)
                .jsonPath("$.message").isNotEmpty()
                .jsonPath("$.detail").isEmpty()
                .jsonPath("$.length()").isEqualTo(3)
                .returnResult()
                .getResponseBody();

        assertThat(new String(body, StandardCharsets.UTF_8))
                .doesNotContain("127.0.0.1", "does-not-exist", "Exception", "refused", "\tat ");
    }

    @Test
    void noRoute() {
        expectError(client.get().uri("/api/catalog").exchange(), 404, "SLHD_GATE_00001");
    }

    @Test
    void connectionRefused() {
        expectError(client.get().uri("/api/eval/settings").exchange(), 503, "SLHD_GATE_00002");
    }

    @Test
    void unknownHost() {
        expectError(client.get().uri("/api/assistant/settings").exchange(), 503, "SLHD_GATE_00002");
    }

    @Test
    void theDestinationClosesBeforeAnswering() {
        MONOLITH.respondWith((request, response) -> {
            response.withConnection(connection -> connection.channel().close());
            return Mono.never();
        });

        expectError(client.get().uri("/api/documents").exchange(), 503, "SLHD_GATE_00002");
    }

    @Test
    void theDestinationDoesNotAnswerInTime() {
        MONOLITH.respondWith((request, response) -> Mono.delay(Duration.ofSeconds(2))
                .then(response.sendString(Mono.just("late")).then()));

        expectError(client.get().uri("/api/knowledge/search").exchange(), 504, "SLHD_GATE_00003");
    }

    @Test
    void aDestinationDownDoesNotAffectTheOthers() {
        client.get().uri("/api/eval/settings").exchange().expectStatus().isEqualTo(503);

        client.get().uri("/api/documents").exchange().expectStatus().isOk();
    }
}
