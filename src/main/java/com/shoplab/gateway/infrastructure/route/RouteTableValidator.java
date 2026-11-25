package com.shoplab.gateway.infrastructure.route;

import com.shoplab.gateway.config.GatewayRoutesProperties;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Turns the configured routes into validated ones, or stops the start-up naming the route that is wrong
 * (specs/LAB-107, FR-010). The messages never repeat a URI, which could carry credentials.
 */
public class RouteTableValidator {

    private static final Pattern NAME = Pattern.compile("[a-z0-9]+");
    private static final Pattern PREFIX = Pattern.compile("/api(/[a-z0-9-]+)+");
    private static final Set<String> SCHEMES = Set.of("http", "https");

    public List<GatewayRoute> resolve(GatewayRoutesProperties properties) {
        Map<String, GatewayRoutesProperties.Route> configured = properties.routes();
        if (configured == null || configured.isEmpty()) {
            throw new InvalidRouteException("shoplab.gateway.routes needs at least one route");
        }
        List<GatewayRoute> routes = new ArrayList<>();
        configured.forEach((name, route) -> routes.add(resolve(name, route, properties)));
        rejectOverlaps(routes);
        return List.copyOf(routes);
    }

    private GatewayRoute resolve(String name, GatewayRoutesProperties.Route route,
            GatewayRoutesProperties properties) {
        if (!NAME.matcher(name).matches()) {
            throw invalid(name, "the name may only have lower case letters and digits");
        }
        String prefix = route.prefix();
        if (prefix == null || !PREFIX.matcher(prefix).matches()) {
            throw invalid(name, "the prefix must be like /api/<segment>, with segments of [a-z0-9-] and no "
                    + "trailing slash or wildcard");
        }
        URI uri = route.uri() == null ? properties.monolithUri() : route.uri();
        if (!isHttpOrigin(uri)) {
            throw invalid(name, "the uri must be http or https with a host, without path, query or credentials");
        }
        Duration timeout = route.responseTimeout() == null
                ? properties.defaultResponseTimeout()
                : route.responseTimeout();
        if (timeout == null || timeout.toMillis() <= 0) {
            throw invalid(name, "the response-timeout must be positive");
        }
        return new GatewayRoute(name, prefix, uri, timeout);
    }

    private boolean isHttpOrigin(URI uri) {
        return uri != null
                && uri.getScheme() != null
                && SCHEMES.contains(uri.getScheme())
                && uri.getHost() != null
                && (uri.getRawPath() == null || uri.getRawPath().isEmpty())
                && uri.getRawQuery() == null
                && uri.getRawUserInfo() == null
                && uri.getRawFragment() == null;
    }

    /** No prefix may repeat or contain another one, so the order of the routes never matters. */
    private void rejectOverlaps(List<GatewayRoute> routes) {
        for (int i = 0; i < routes.size(); i++) {
            for (int j = i + 1; j < routes.size(); j++) {
                GatewayRoute first = routes.get(i);
                GatewayRoute second = routes.get(j);
                if (covers(first.prefix(), second.prefix()) || covers(second.prefix(), first.prefix())) {
                    throw new InvalidRouteException("Routes '" + first.name() + "' and '" + second.name()
                            + "' have overlapping prefixes");
                }
            }
        }
    }

    private boolean covers(String outer, String inner) {
        return inner.equals(outer) || inner.startsWith(outer + "/");
    }

    private InvalidRouteException invalid(String name, String reason) {
        return new InvalidRouteException("Invalid route '" + name + "': " + reason);
    }
}
