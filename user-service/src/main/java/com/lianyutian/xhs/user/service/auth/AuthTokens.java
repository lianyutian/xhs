package com.lianyutian.xhs.user.service.auth;

public record AuthTokens(String accessToken, String refreshToken, String errorCode) {

    public static AuthTokens success(String accessToken, String refreshToken) {
        return new AuthTokens(accessToken, refreshToken, null);
    }

    public static AuthTokens failure(String errorCode) {
        return new AuthTokens(null, null, errorCode);
    }
}
