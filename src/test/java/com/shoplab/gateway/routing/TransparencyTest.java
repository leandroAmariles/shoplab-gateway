package com.shoplab.gateway.routing;

import static org.assertj.core.api.Assertions.assertThat;

import com.shoplab.gateway.support.AbstractGatewayTest;
import com.shoplab.gateway.support.StubDestination;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import reactor.core.publisher.Mono;

class TransparencyTest extends AbstractGatewayTest {

    @ParameterizedTest
    @ValueSource(strings = {"GET", "POST", "PUT", "DELETE", "PATCH"})
    void theMethodReachesTheDestination(String method) {
        client.method(HttpMethod.valueOf(method)).uri("/api/documents/1").exchange().expectStatus().isOk();

        assertThat(MONOLITH.received()).extracting("method").containsExactly(method);
    }

    @Test
    void thePathQueryAndBodyArriveUnchanged() {
        // A URI, not a String: the client would encode the percent signs of a String a second time.
        client.put().uri(URI.create(baseUrl() + "/api/chat/feedback/9?query=c%C3%B3mo+devuelvo&limit=5"))
                .header("Content-Type", "application/json")
                .bodyValue("{\"rating\":\"up\"}")
                .exchange()
                .expectStatus().isOk();

        StubDestination.Received received = MONOLITH.received().getFirst();
        assertThat(received.uri()).isEqualTo("/api/chat/feedback/9?query=c%C3%B3mo+devuelvo&limit=5");
        assertThat(new String(received.body(), StandardCharsets.UTF_8)).isEqualTo("{\"rating\":\"up\"}");
    }

    @Test
    void theHostAndTheCredentialsArriveUnchanged() {
        client.post().uri("/api/eval/runs")
                .header("Authorization", "Bearer abc")
                .header("X-Eval-Token", "eval-token")
                .header("X-Prompt-Token", "prompt-token")
                .header("X-Prompt-Versions", "system=3")
                .exchange()
                .expectStatus().isOk();

        var headers = MONOLITH.received().getFirst().headers();
        assertThat(headers.get("Host")).isEqualTo("127.0.0.1:" + port);
        assertThat(headers.get("Authorization")).isEqualTo("Bearer abc");
        assertThat(headers.get("X-Eval-Token")).isEqualTo("eval-token");
        assertThat(headers.get("X-Prompt-Token")).isEqualTo("prompt-token");
        assertThat(headers.get("X-Prompt-Versions")).isEqualTo("system=3");
    }

    /**
     * Secure default of the gateway: without trusted proxies, forwarding headers are dropped, so a client cannot
     * claim another origin. No destination reads them today; LAB-60 revisits it if it limits by address.
     */
    @Test
    void forwardingHeadersFromAnUntrustedSenderAreDropped() {
        client.get().uri("/api/documents")
                .header("Forwarded", "for=10.0.0.7")
                .header("X-Forwarded-For", "10.0.0.7")
                .header("X-Forwarded-Proto", "https")
                .exchange()
                .expectStatus().isOk();

        var headers = MONOLITH.received().getFirst().headers();
        assertThat(headers.get("Forwarded")).isNull();
        assertThat(headers.get("X-Forwarded-For")).isNull();
        assertThat(headers.get("X-Forwarded-Proto")).isNull();
    }

    @ParameterizedTest
    @ValueSource(ints = {201, 400, 404, 409, 413, 429, 500, 503})
    void theStatusHeadersAndBodyOfTheDestinationPassThrough(int status) {
        String body = "{\"code\":\"SLHD_RETR_00009\",\"message\":\"from the destination\"}";
        MONOLITH.respondWith((request, response) -> response.status(status)
                .header("Content-Type", "application/json")
                .header("Location", "/api/documents/5")
                .sendString(Mono.just(body)));

        client.get().uri("/api/documents").exchange()
                .expectStatus().isEqualTo(status)
                .expectHeader().valueEquals("Location", "/api/documents/5")
                .expectBody(String.class).isEqualTo(body);
    }
}
