package com.shoplab.gateway.shared.error;

/** A request the gateway itself rejects. */
public class GatewayException extends RuntimeException {

    private final transient ResponseCodeEnum responseCode;

    public GatewayException(ResponseCodeEnum responseCode) {
        super(responseCode.getMessage());
        this.responseCode = responseCode;
    }

    public ResponseCodeEnum getResponseCode() {
        return responseCode;
    }
}
