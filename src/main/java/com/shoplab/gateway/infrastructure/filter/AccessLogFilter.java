package com.shoplab.gateway.infrastructure.filter;

import com.shoplab.gateway.shared.error.GlobalExceptionHandler;
import java.net.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * One line per request with the route, destination, status and duration. Never the path, query, headers or body: a
 * search query carries text written by a person (specs/LAB-107, FR-016).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class AccessLogFilter implements WebFilter {

    private static final Logger LOG = LoggerFactory.getLogger(AccessLogFilter.class);
    private static final String MANAGEMENT_PREFIX = "/actuator/";
    private static final String MANAGEMENT_ROOT = "/actuator";
    private static final String NONE = "-";

    private final GlobalExceptionHandler errorHandler;

    public AccessLogFilter(GlobalExceptionHandler errorHandler) {
        this.errorHandler = errorHandler;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        if (path.equals(MANAGEMENT_ROOT) || path.startsWith(MANAGEMENT_PREFIX)) {
            return chain.filter(exchange);
        }
        long start = System.nanoTime();
        // The error is turned into its response here, so the line carries the status the client receives.
        return chain.filter(exchange)
                .onErrorResume(error -> errorHandler.handle(exchange, error))
                .doFinally(signal -> log(exchange, start));
    }

    private void log(ServerWebExchange exchange, long start) {
        Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
        HttpStatusCode status = exchange.getResponse().getStatusCode();
        LOG.info("access requestId={} routeId={} destination={} method={} status={} durationMs={}",
                exchange.getAttribute(CorrelationFilter.REQUEST_ID_ATTRIBUTE),
                route == null ? NONE : route.getId(),
                route == null ? NONE : origin(route.getUri()),
                exchange.getRequest().getMethod(),
                status == null ? 200 : status.value(),
                (System.nanoTime() - start) / 1_000_000);
    }

    private String origin(URI uri) {
        return uri.getScheme() + "://" + uri.getHost() + ":" + uri.getPort();
    }
}
