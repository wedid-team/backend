package com.wedid.backend.auth.service;

/** 리프레시 토큰이 없거나, 만료됐거나, 이미 폐기된 경우. 다시 로그인해야 한다. */
public class InvalidRefreshTokenException extends RuntimeException {

    public InvalidRefreshTokenException() {
        super("로그인이 만료되었습니다. 다시 로그인해 주세요.");
    }
}
