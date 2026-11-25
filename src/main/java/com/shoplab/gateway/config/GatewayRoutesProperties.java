package com.shoplab.gateway.config;

import java.net.URI;
import java.time.Duration;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The route table as configured. It is a map so that one entry can be overridden or added from the environment
 * without replacing the others (specs/LAB-107, research D-3). Validation lives in {@code RouteTableValidator}.
 *
 * <p>Each {@link Route} has a public path {@code prefix} (for example {@code /api/chat}), the origin {@code uri} of its
 * destination (the monolith when absent) and the {@code responseTimeout} allowed until the response headers arrive.
 *
 * @param monolithUri where a route without its own {@code uri} goes
 * @param defaultResponseTimeout used by a route without its own timeout
 * @param routes the routes by name
 */
@ConfigurationProperties("shoplab.gateway")
public record GatewayRoutesProperties(URI monolithUri, Duration defaultResponseTimeout, Map<String, Route> routes) {

    public record Route(String prefix, URI uri, Duration responseTimeout) {
    }
}
