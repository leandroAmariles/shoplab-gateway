package com.shoplab.gateway.shared.error;

/** Error body, compatible with {@code ApiErrorResponse} of shoplab-contracts (specs/LAB-107, research D-9). */
public record ApiError(String code, String message, String detail) {

    static ApiError of(ResponseCodeEnum code) {
        return new ApiError(code.getCode(), code.getMessage(), null);
    }
}
