package com.shoplab.gateway.routing;

import static org.assertj.core.api.Assertions.assertThat;

import com.shoplab.gateway.GatewayApplication;
import com.shoplab.gateway.support.StubDestination;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.web.reactive.server.WebTestClient;

/** FR-020: a prefix moves from the monolith to a service, and back, changing only the configuration. */
class RouteMoveTest {

    private static final StubDestination MONOLITH = StubDestination.start("monolith");
    private static final StubDestination SERVICE = StubDestination.start("retrieval-service");

    @AfterAll
    static void stopStubs() {
        MONOLITH.close();
        SERVICE.close();
    }

    @BeforeEach
    void forgetRequests() {
        MONOLITH.reset();
        SERVICE.reset();
    }

    private static ConfigurableApplicationContext start(String... configuration) {
        List<String> arguments = new ArrayList<>(List.of(
                "--server.port=0", "--shoplab.gateway.monolith-uri=" + MONOLITH.url()));
        arguments.addAll(List.of(configuration));
        return new SpringApplicationBuilder(GatewayApplication.class).run(arguments.toArray(String[]::new));
    }

    private static WebTestClient clientOf(ConfigurableApplicationContext gateway) {
        return WebTestClient.bindToServer()
                .baseUrl("http://127.0.0.1:" + gateway.getEnvironment().getProperty("local.server.port"))
                .build();
    }

    private static String answerOf(WebTestClient client, String path) {
        return client.get().uri(path).exchange().expectStatus().isOk()
                .expectBody(String.class).returnResult().getResponseBody();
    }

    @Test
    void onlyTheMovedPrefixChangesDestinationAndItCanBeReverted() {
        try (var moved = start("--shoplab.gateway.routes.documents.uri=" + SERVICE.url())) {
            WebTestClient client = clientOf(moved);
            assertThat(answerOf(client, "/api/documents")).isEqualTo("retrieval-service");
            for (String untouched : new String[] {"/api/knowledge/search", "/api/chat", "/api/eval/settings",
                    "/api/assistant/settings"}) {
                assertThat(answerOf(client, untouched)).as(untouched).isEqualTo("monolith");
            }
        }
        try (var reverted = start()) {
            assertThat(answerOf(clientOf(reverted), "/api/documents")).isEqualTo("monolith");
        }
    }

    @Test
    void theMovedServiceReceivesTheSameRequest() {
        try (var moved = start("--shoplab.gateway.routes.documents.uri=" + SERVICE.url())) {
            clientOf(moved).post().uri("/api/documents/7?force=true")
                    .header("X-Eval-Token", "token")
                    .bodyValue("content")
                    .exchange().expectStatus().isOk();
        }

        StubDestination.Received received = SERVICE.received().getFirst();
        assertThat(received.method()).isEqualTo("POST");
        assertThat(received.uri()).isEqualTo("/api/documents/7?force=true");
        assertThat(received.headers().get("X-Eval-Token")).isEqualTo("token");
        assertThat(new String(received.body(), StandardCharsets.UTF_8)).isEqualTo("content");
        assertThat(MONOLITH.received()).isEmpty();
    }

    @Test
    void thePrefixesOfOneContextMoveSeparatelyOrTogether() {
        try (var moved = start("--shoplab.gateway.routes.documents.uri=" + SERVICE.url(),
                "--shoplab.gateway.routes.knowledge.uri=" + SERVICE.url())) {
            WebTestClient client = clientOf(moved);
            assertThat(answerOf(client, "/api/documents")).isEqualTo("retrieval-service");
            assertThat(answerOf(client, "/api/knowledge/search")).isEqualTo("retrieval-service");
            assertThat(answerOf(client, "/api/chat")).isEqualTo("monolith");
        }
    }
}
