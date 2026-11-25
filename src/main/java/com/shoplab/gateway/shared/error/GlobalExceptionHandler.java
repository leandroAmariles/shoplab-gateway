package com.shoplab.gateway.shared.error;

import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebExceptionHandler;
import reactor.core.publisher.Mono;
import tools.jackson.databind.json.JsonMapper;

/**
 * The only place where a failure becomes an HTTP response. It answers in the standard error format without internal
 * details: no host names, addresses or exception messages. It runs before the default handler of Spring Boot.
 */
@Component
@Order(-2)
public class GlobalExceptionHandler implements WebExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final JsonMapper jsonMapper;

    public GlobalExceptionHandler(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable error) {
        ServerHttpResponse response = exchange.getResponse();
        if (response.isCommitted()) {
            // The status is already on the wire: failing is what tells the client the stream is not complete.
            return Mono.error(error);
        }
        ResponseCodeEnum code = codeOf(error);
        HttpStatusCode status = statusOf(error, code);
        log(code, status, error);

        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        DataBuffer body = response.bufferFactory().wrap(jsonMapper.writeValueAsBytes(ApiError.of(code)));
        return response.writeWith(Mono.just(body));
    }

    private ResponseCodeEnum codeOf(Throwable error) {
        if (error instanceof GatewayException gateway) {
            return gateway.getResponseCode();
        }
        if (error instanceof ResponseStatusException statusError) {
            if (statusError.getStatusCode().isSameCodeAs(HttpStatus.NOT_FOUND)) {
                return ResponseCodeEnum.ROUTE_NOT_FOUND;
            }
            if (statusError.getStatusCode().isSameCodeAs(HttpStatus.GATEWAY_TIMEOUT)) {
                return ResponseCodeEnum.DESTINATION_TIMEOUT;
            }
            return ResponseCodeEnum.UNEXPECTED_ERROR;
        }
        // Refused, unknown host, closed before answering: all of them reach here as an IOException.
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof IOException) {
                return ResponseCodeEnum.DESTINATION_UNAVAILABLE;
            }
        }
        return ResponseCodeEnum.UNEXPECTED_ERROR;
    }

    private HttpStatusCode statusOf(Throwable error, ResponseCodeEnum code) {
        return error instanceof ResponseStatusException statusError ? statusError.getStatusCode() : code.getStatus();
    }

    private void log(ResponseCodeEnum code, HttpStatusCode status, Throwable error) {
        if (code == ResponseCodeEnum.UNEXPECTED_ERROR && status.is5xxServerError()) {
            LOG.error("Gateway failure: code={}", code.getCode(), error);
        } else if (status.is5xxServerError()) {
            LOG.warn("Destination failure: code={}, cause={}", code.getCode(), error.getClass().getSimpleName());
        }
    }
}
