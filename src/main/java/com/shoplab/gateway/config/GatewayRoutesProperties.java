package com.shoplab.gateway.config;

import java.net.URI;
import java.time.Duration;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The route table as configured. It is a map so that one entry can be overridden or added from the environment
 * without replacing the others (specs/LAB-107, research D-3). Validation lives in {@code RouteTableValidator}.
 *
 * @param monolithUri where a route without its own {@code uri} goes
 * @param defaultResponseTimeout used by a route without its own timeout
 * @param routes the routes by name
 */
@ConfigurationProperties("shoplab.gateway")
public record GatewayRoutesProperties(URI monolithUri, Duration defaultResponseTimeout, Map<String, Route> routes) {

    /**
     * One configured route.
     *
     * @param prefix public path prefix, for example {@code /api/chat}
     * @param uri origin of the destination; the monolith when absent
     * @param responseTimeout time allowed until the response headers arrive
     */
    public record Route(String prefix, URI uri, Duration responseTimeout) {
    }
}
