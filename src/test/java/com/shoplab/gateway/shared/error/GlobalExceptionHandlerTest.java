package com.shoplab.gateway.shared.error;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.ConnectException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ResponseStatusException;
import reactor.test.StepVerifier;
import tools.jackson.databind.json.JsonMapper;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler(JsonMapper.builder().build());

    private final MockServerWebExchange exchange =
            MockServerWebExchange.from(MockServerHttpRequest.get("/api/chat"));

    private void expect(Throwable error, int status, String code) {
        StepVerifier.create(handler.handle(exchange, error)).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode().value()).isEqualTo(status);
        assertThat(exchange.getResponse().getBodyAsString().block()).contains(code);
    }

    @Test
    void anUnexpectedFailureHidesItsMessage() {
        expect(new IllegalStateException("secret internal state"), 500, "SLHD_GATE_00004");

        assertThat(exchange.getResponse().getBodyAsString().block()).doesNotContain("secret");
    }

    @Test
    void aWrappedConnectionFailureIsAnUnavailableDestination() {
        expect(new IllegalStateException(new ConnectException("refused")), 503, "SLHD_GATE_00002");
    }

    @Test
    void anotherStatusOfTheFrameworkIsKept() {
        expect(new ResponseStatusException(HttpStatus.BAD_REQUEST), 400, "SLHD_GATE_00004");
    }

    @Test
    void aTypedGatewayFailureKeepsItsCode() {
        expect(new GatewayException(ResponseCodeEnum.ROUTE_NOT_FOUND), 404, "SLHD_GATE_00001");
    }

    @Test
    void aResponseAlreadySentCannotBeReplaced() {
        exchange.getResponse().setComplete().block();

        StepVerifier.create(handler.handle(exchange, new IllegalStateException("late")))
                .verifyErrorMessage("late");
    }
}
