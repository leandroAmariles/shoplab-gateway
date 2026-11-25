package com.shoplab.gateway.infrastructure.route;

/** The configured route table cannot be used; the gateway must not start. */
public class InvalidRouteException extends RuntimeException {

    public InvalidRouteException(String message) {
        super(message);
    }
}
