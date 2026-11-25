package com.shoplab.gateway.infrastructure.filter;

import static org.assertj.core.api.Assertions.assertThat;

import com.shoplab.gateway.support.AbstractGatewayTest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import reactor.core.publisher.Mono;

class CorrelationFilterTest extends AbstractGatewayTest {

    private static final String TRACEPARENT = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01";

    private List<String> requestIdsOf(String uri, String sent) {
        var request = client.get().uri(uri);
        if (sent != null) {
            request.header("X-Request-Id", sent);
        }
        return request.exchange().returnResult(String.class).getResponseHeaders().get("X-Request-Id");
    }

    @Test
    void aSafeRequestIdIsKeptAllTheWay() {
        List<String> returned = requestIdsOf("/api/documents", "lab107-check_1.a");

        assertThat(returned).containsExactly("lab107-check_1.a");
        assertThat(MONOLITH.received().getFirst().headers().getAll("X-Request-Id"))
                .containsExactly("lab107-check_1.a");
    }

    @ParameterizedTest
    @ValueSource(strings = {"has space", "quote\"d",
            "12345678901234567890123456789012345678901234567890123456789012345"})
    void anUnsafeRequestIdIsReplaced(String unsafe) {
        List<String> returned = requestIdsOf("/api/documents", unsafe);

        assertThat(returned).hasSize(1).doesNotContain(unsafe);
        assertThat(MONOLITH.received().getFirst().headers().get("X-Request-Id")).isEqualTo(returned.getFirst());
    }

    @Test
    void aMissingRequestIdIsGeneratedAsAUuid() {
        assertThat(requestIdsOf("/api/documents", null)).singleElement().asString()
                .matches("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}");
    }

    @Test
    void theClientGetsOneRequestIdEvenIfTheDestinationAddsItsOwn() {
        MONOLITH.respondWith((request, response) -> response
                .header("X-Request-Id", request.requestHeaders().get("X-Request-Id"))
                .sendString(Mono.just("ok")));

        assertThat(requestIdsOf("/api/documents", "same-id")).containsExactly("same-id");
    }

    @Test
    void anErrorOfTheGatewayAlsoCarriesTheRequestId() {
        assertThat(requestIdsOf("/api/catalog", "not-routed")).containsExactly("not-routed");
    }

    @Test
    void theTraceContextIsKept() {
        client.get().uri("/api/documents").header("traceparent", TRACEPARENT).exchange().expectStatus().isOk();

        assertThat(MONOLITH.received().getFirst().headers().get("traceparent")).isEqualTo(TRACEPARENT);
    }

    @Test
    void aMissingTraceContextIsGeneratedInW3cFormat() {
        client.get().uri("/api/documents").exchange().expectStatus().isOk();

        assertThat(MONOLITH.received().getFirst().headers().get("traceparent"))
                .matches("00-[0-9a-f]{32}-[0-9a-f]{16}-00");
    }
}
