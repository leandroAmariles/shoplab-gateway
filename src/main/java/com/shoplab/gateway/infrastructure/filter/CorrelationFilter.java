package com.shoplab.gateway.infrastructure.filter;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Gives every request one correlation id and a W3C trace context, keeps the ones that arrive and sends both to the
 * destination. The id follows the rule of the backend, so both log the same value.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationFilter implements WebFilter {

    static final String REQUEST_ID_HEADER = "X-Request-Id";
    static final String TRACEPARENT_HEADER = "traceparent";
    static final String REQUEST_ID_ATTRIBUTE = "requestId";

    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");
    private static final long UUID_VERSION_MASK = 0xF000L;
    private static final long UUID_VERSION_4 = 0x4000L;
    private static final long UUID_VARIANT_MASK = 0xC000000000000000L;
    private static final long UUID_VARIANT_RFC_4122 = 0x8000000000000000L;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String received = exchange.getRequest().getHeaders().getFirst(REQUEST_ID_HEADER);
        String requestId = received != null && SAFE_ID.matcher(received).matches() ? received : newRequestId();
        String traceparent = exchange.getRequest().getHeaders().getFirst(TRACEPARENT_HEADER);
        String trace = traceparent == null || traceparent.isBlank() ? newTraceparent() : traceparent;

        ServerWebExchange correlated = exchange.mutate()
                .request(request -> request.headers(headers -> {
                    headers.set(REQUEST_ID_HEADER, requestId);
                    headers.set(TRACEPARENT_HEADER, trace);
                }))
                .build();
        correlated.getAttributes().put(REQUEST_ID_ATTRIBUTE, requestId);
        // Set just before sending: the destination may add the same header, and the client must get it once.
        correlated.getResponse().beforeCommit(() -> {
            correlated.getResponse().getHeaders().set(REQUEST_ID_HEADER, requestId);
            return Mono.empty();
        });
        return chain.filter(correlated);
    }

    /** UUID shaped, without {@code UUID.randomUUID()}, which blocks on Linux and must not run on the event loop. */
    private String newRequestId() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        long mostSignificant = (random.nextLong() & ~UUID_VERSION_MASK) | UUID_VERSION_4;
        long leastSignificant = (random.nextLong() & ~UUID_VARIANT_MASK) | UUID_VARIANT_RFC_4122;
        return new UUID(mostSignificant, leastSignificant).toString();
    }

    /** Flags 00: the gateway records no span, so it does not claim the trace is sampled. */
    private String newTraceparent() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        return String.format("00-%016x%016x-%016x-00",
                random.nextLong(1, Long.MAX_VALUE), random.nextLong(), random.nextLong(1, Long.MAX_VALUE));
    }
}
