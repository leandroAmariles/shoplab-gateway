package com.shoplab.gateway.infrastructure.route;

import java.net.URI;
import java.time.Duration;

/** A validated route, with every default already applied. */
public record GatewayRoute(String name, String prefix, URI uri, Duration responseTimeout) {
}
