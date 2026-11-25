package com.shoplab.gateway.shared.error;

import org.springframework.http.HttpStatus;

/** Codes of the errors born in the gateway; the errors of a destination pass through untouched. */
public enum ResponseCodeEnum {

    ROUTE_NOT_FOUND("SLHD_GATE_00001", "No route for the request", HttpStatus.NOT_FOUND),
    DESTINATION_UNAVAILABLE("SLHD_GATE_00002", "The destination service is unavailable",
            HttpStatus.SERVICE_UNAVAILABLE),
    DESTINATION_TIMEOUT("SLHD_GATE_00003", "The destination service did not respond in time",
            HttpStatus.GATEWAY_TIMEOUT),
    UNEXPECTED_ERROR("SLHD_GATE_00004", "Unexpected gateway error", HttpStatus.INTERNAL_SERVER_ERROR);

    private final String code;
    private final String message;
    private final HttpStatus status;

    ResponseCodeEnum(String code, String message, HttpStatus status) {
        this.code = code;
        this.message = message;
        this.status = status;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
