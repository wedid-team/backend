package com.wedid.backend.auth.web;

import com.wedid.backend.auth.config.AuthProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;

/**
 * 인증 토큰 쿠키 생성·삭제·조회.
 * 두 토큰 모두 HttpOnly(스크립트 접근 불가) + SameSite=Lax로 내려 CSRF를 막는다.
 * 그래서 프론트엔드와 API는 같은 사이트(예: wedid.app / api.wedid.app)에 있어야 한다.
 */
@Component
public class AuthCookieManager {

    public static final String ACCESS_TOKEN_COOKIE = "wedid_access_token";
    public static final String REFRESH_TOKEN_COOKIE = "wedid_refresh_token";

    /** 리프레시 토큰은 갱신·로그아웃 API에만 전송되도록 경로를 좁힌다. */
    static final String REFRESH_TOKEN_PATH = "/api/v1/auth";

    private static final String SAME_SITE = "Lax";

    private final AuthProperties properties;

    public AuthCookieManager(AuthProperties properties) {
        this.properties = properties;
    }

    public void writeTokens(HttpServletResponse response, String accessToken, String refreshToken) {
        addCookie(response, ACCESS_TOKEN_COOKIE, accessToken, "/", properties.accessTokenTtl());
        addCookie(response, REFRESH_TOKEN_COOKIE, refreshToken, REFRESH_TOKEN_PATH, properties.refreshTokenTtl());
    }

    public void clearTokens(HttpServletResponse response) {
        addCookie(response, ACCESS_TOKEN_COOKIE, "", "/", Duration.ZERO);
        addCookie(response, REFRESH_TOKEN_COOKIE, "", REFRESH_TOKEN_PATH, Duration.ZERO);
    }

    public Optional<String> readAccessToken(HttpServletRequest request) {
        return read(request, ACCESS_TOKEN_COOKIE);
    }

    public Optional<String> readRefreshToken(HttpServletRequest request) {
        return read(request, REFRESH_TOKEN_COOKIE);
    }

    private void addCookie(HttpServletResponse response, String name, String value, String path, Duration maxAge) {
        ResponseCookie cookie = ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(properties.cookieSecure())
                .sameSite(SAME_SITE)
                .path(path)
                .maxAge(maxAge)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private Optional<String> read(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        return Arrays.stream(cookies)
                .filter(cookie -> name.equals(cookie.getName()))
                .map(Cookie::getValue)
                .filter(value -> !value.isBlank())
                .findFirst();
    }
}
