package com.shoplab.gateway.infrastructure.route;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shoplab.gateway.config.GatewayRoutesProperties;
import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RouteTableValidatorTest {

    private static final URI MONOLITH = URI.create("http://monolith:8080");

    private final RouteTableValidator validator = new RouteTableValidator();

    private static GatewayRoutesProperties properties(Map<String, GatewayRoutesProperties.Route> routes) {
        return new GatewayRoutesProperties(MONOLITH, Duration.ofSeconds(120), routes);
    }

    private static GatewayRoutesProperties.Route route(String prefix) {
        return new GatewayRoutesProperties.Route(prefix, null, null);
    }

    private static GatewayRoutesProperties single(String name, String prefix, URI uri, Duration timeout) {
        return properties(Map.of(name, new GatewayRoutesProperties.Route(prefix, uri, timeout)));
    }

    @Test
    void aRouteWithoutUriOrTimeoutTakesTheMonolithAndTheDefaultTimeout() {
        var resolved = validator.resolve(single("chat", "/api/chat", null, null));

        assertThat(resolved).containsExactly(
                new GatewayRoute("chat", "/api/chat", MONOLITH, Duration.ofSeconds(120)));
    }

    @Test
    void aRouteKeepsItsOwnUriAndTimeout() {
        URI service = URI.create("https://retrieval-service:8443");

        var resolved = validator.resolve(single("documents", "/api/documents", service, Duration.ofSeconds(5)));

        assertThat(resolved).containsExactly(
                new GatewayRoute("documents", "/api/documents", service, Duration.ofSeconds(5)));
    }

    @Test
    void thereMustBeAtLeastOneRoute() {
        assertThatThrownBy(() -> validator.resolve(properties(Map.of())))
                .isInstanceOf(InvalidRouteException.class)
                .hasMessageContaining("at least one route");
    }

    @ParameterizedTest
    @ValueSource(strings = {"Chat", "chat-service", "chat_x"})
    void aNameHasOnlyLowerCaseLettersAndDigits(String name) {
        assertThatThrownBy(() -> validator.resolve(single(name, "/api/chat", null, null)))
                .isInstanceOf(InvalidRouteException.class)
                .hasMessageContaining(name);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "/chat", "/api", "/api/", "/api/chat/", "/api/chat/**", "/api/Chat", "/api//chat",
            "/api/../actuator", "api/chat"})
    void aPrefixIsMadeOfPlainSegmentsUnderApi(String prefix) {
        assertThatThrownBy(() -> validator.resolve(single("chat", prefix, null, null)))
                .isInstanceOf(InvalidRouteException.class)
                .hasMessageContaining("chat")
                .hasMessageContaining("prefix");
    }

    @Test
    void aPrefixIsRequired() {
        assertThatThrownBy(() -> validator.resolve(single("catalog", null, null, null)))
                .isInstanceOf(InvalidRouteException.class)
                .hasMessageContaining("catalog")
                .hasMessageContaining("prefix");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "monolith:8080", "ftp://monolith", "http:/monolith", "http://monolith/base",
            "http://monolith?x=1", "http://user:secret@monolith", "http://monolith#x"})
    void aUriIsAnHttpOriginWithoutPathQueryOrCredentials(String uri) {
        assertThatThrownBy(() -> validator.resolve(single("chat", "/api/chat", URI.create(uri), null)))
                .isInstanceOf(InvalidRouteException.class)
                .hasMessageContaining("chat")
                .hasMessageContaining("uri")
                .hasMessageNotContaining("secret");
    }

    @Test
    void theMonolithUriIsValidatedForTheRoutesThatUseIt() {
        var invalid = new GatewayRoutesProperties(null, Duration.ofSeconds(1), Map.of("chat", route("/api/chat")));

        assertThatThrownBy(() -> validator.resolve(invalid))
                .isInstanceOf(InvalidRouteException.class)
                .hasMessageContaining("chat")
                .hasMessageContaining("uri");
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1})
    void aTimeoutIsPositive(long millis) {
        assertThatThrownBy(() -> validator.resolve(single("chat", "/api/chat", null, Duration.ofMillis(millis))))
                .isInstanceOf(InvalidRouteException.class)
                .hasMessageContaining("chat")
                .hasMessageContaining("response-timeout");
    }

    @Test
    void twoRoutesCannotShareAPrefix() {
        var routes = new LinkedHashMap<String, GatewayRoutesProperties.Route>();
        routes.put("chat", route("/api/chat"));
        routes.put("other", route("/api/chat"));

        assertThatThrownBy(() -> validator.resolve(properties(routes)))
                .isInstanceOf(InvalidRouteException.class)
                .hasMessageContaining("chat")
                .hasMessageContaining("other");
    }

    @Test
    void aPrefixCannotContainAnotherBySegments() {
        var routes = new LinkedHashMap<String, GatewayRoutesProperties.Route>();
        routes.put("eval", route("/api/eval"));
        routes.put("runs", route("/api/eval/runs"));

        assertThatThrownBy(() -> validator.resolve(properties(routes)))
                .isInstanceOf(InvalidRouteException.class)
                .hasMessageContaining("eval")
                .hasMessageContaining("runs");
    }

    @Test
    void similarPrefixesThatDoNotContainEachOtherAreAccepted() {
        var routes = new LinkedHashMap<String, GatewayRoutesProperties.Route>();
        routes.put("chat", route("/api/chat"));
        routes.put("chatbot", route("/api/chatbot"));

        assertThat(validator.resolve(properties(routes))).hasSize(2);
    }
}
