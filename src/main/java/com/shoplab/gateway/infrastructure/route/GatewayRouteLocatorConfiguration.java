package com.shoplab.gateway.infrastructure.route;

import com.shoplab.gateway.config.GatewayRoutesProperties;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.cloud.gateway.support.RouteMetadataUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Publishes one gateway route per configured entry. Nothing is rewritten: the path, the {@code Host} and the body
 * reach the destination as the client sent them (specs/LAB-107, FR-007).
 */
@Configuration(proxyBeanMethods = false)
public class GatewayRouteLocatorConfiguration {

    @Bean
    RouteLocator shoplabRoutes(RouteLocatorBuilder builder, GatewayRoutesProperties properties) {
        RouteLocatorBuilder.Builder routes = builder.routes();
        for (GatewayRoute route : new RouteTableValidator().resolve(properties)) {
            routes.route(route.name(), spec -> spec
                    .path(route.prefix(), route.prefix() + "/**")
                    .filters(filters -> filters.preserveHostHeader())
                    .metadata(RouteMetadataUtils.RESPONSE_TIMEOUT_ATTR, route.responseTimeout().toMillis())
                    .uri(route.uri()));
        }
        return routes.build();
    }
}
