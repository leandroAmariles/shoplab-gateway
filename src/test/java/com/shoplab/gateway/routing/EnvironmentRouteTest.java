package com.shoplab.gateway.routing;

import static org.assertj.core.api.Assertions.assertThat;

import com.shoplab.gateway.GatewayApplication;
import com.shoplab.gateway.support.StubDestination;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

/** FR-006: the documented environment variables move a route and add a new one, as a deployment would. */
class EnvironmentRouteTest {

    @Test
    void environmentVariablesMoveARouteAndAddANewOne() {
        try (var monolith = StubDestination.start("monolith");
                var retrieval = StubDestination.start("retrieval-service");
                var catalog = StubDestination.start("catalog-service")) {
            Map<String, Object> variables = Map.of(
                    "GATEWAY_MONOLITH_URI", monolith.url(),
                    "SHOPLAB_GATEWAY_ROUTES_DOCUMENTS_URI", retrieval.url(),
                    "SHOPLAB_GATEWAY_ROUTES_CATALOG_PREFIX", "/api/catalog",
                    "SHOPLAB_GATEWAY_ROUTES_CATALOG_URI", catalog.url());
            var environment = new StandardEnvironment() {
                @Override
                protected void customizePropertySources(MutablePropertySources sources) {
                    sources.addLast(new SystemEnvironmentPropertySource(
                            SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, variables));
                }
            };

            try (var gateway = new SpringApplicationBuilder(GatewayApplication.class)
                    .environment(environment)
                    .run("--server.port=0")) {
                WebTestClient client = WebTestClient.bindToServer()
                        .baseUrl("http://127.0.0.1:" + environment.getProperty("local.server.port"))
                        .build();

                assertThat(answerOf(client, "/api/documents")).isEqualTo("retrieval-service");
                assertThat(answerOf(client, "/api/catalog/products")).isEqualTo("catalog-service");
                assertThat(answerOf(client, "/api/chat")).isEqualTo("monolith");
            }
        }
    }

    private static String answerOf(WebTestClient client, String path) {
        return client.get().uri(path).exchange().expectStatus().isOk()
                .expectBody(String.class).returnResult().getResponseBody();
    }
}
