package com.shoplab.gateway.infrastructure.filter;

import com.shoplab.gateway.shared.error.GatewayException;
import com.shoplab.gateway.shared.error.ResponseCodeEnum;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import org.springframework.web.util.UriUtils;
import reactor.core.publisher.Mono;

/**
 * Rejects dot segments, literal or encoded: a destination could resolve them and serve, under a routed prefix, a path
 * that is not published, such as its {@code /actuator} (specs/LAB-107, FR-009).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 2)
public class UnsafePathFilter implements WebFilter {

    private static final Pattern SEPARATOR = Pattern.compile("[/\\\\]");

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (isUnsafe(exchange.getRequest().getURI().getRawPath())) {
            return Mono.error(new GatewayException(ResponseCodeEnum.ROUTE_NOT_FOUND));
        }
        return chain.filter(exchange);
    }

    private boolean isUnsafe(String rawPath) {
        String decoded;
        try {
            decoded = UriUtils.decode(rawPath, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException malformed) {
            return true;
        }
        for (String segment : SEPARATOR.split(decoded)) {
            // Anything after ';' is a matrix parameter, which some servers drop before resolving the segment.
            int parameters = segment.indexOf(';');
            String name = parameters < 0 ? segment : segment.substring(0, parameters);
            if (".".equals(name) || "..".equals(name)) {
                return true;
            }
        }
        return false;
    }
}
