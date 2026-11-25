package com.wedid.backend.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * 로그인·토큰 관련 설정 (application.properties의 wedid.auth.*).
 *
 * @param frontendBaseUrl 로그인 성공·실패 후 돌려보낼 프론트엔드 주소
 * @param jwtSecret       액세스 토큰(HS256) 서명 키. 32바이트 이상 필수
 * @param accessTokenTtl  액세스 토큰 유효기간
 * @param refreshTokenTtl 리프레시 토큰 유효기간
 * @param cookieSecure    인증 쿠키 Secure 속성 (HTTPS 환경에서는 true)
 */
@ConfigurationProperties(prefix = "wedid.auth")
public record AuthProperties(
        String frontendBaseUrl,
        String jwtSecret,
        Duration accessTokenTtl,
        Duration refreshTokenTtl,
        boolean cookieSecure
) {

    private static final int MIN_SECRET_BYTES = 32;

    public AuthProperties {
        if (frontendBaseUrl == null || frontendBaseUrl.isBlank()) {
            throw new IllegalStateException("wedid.auth.frontend-base-url 값이 필요합니다.");
        }
        if (jwtSecret == null || jwtSecret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("JWT_SECRET은 32바이트 이상이어야 합니다.");
        }
        if (accessTokenTtl == null || accessTokenTtl.isNegative() || accessTokenTtl.isZero()) {
            throw new IllegalStateException("wedid.auth.access-token-ttl은 0보다 커야 합니다.");
        }
        if (refreshTokenTtl == null || refreshTokenTtl.isNegative() || refreshTokenTtl.isZero()) {
            throw new IllegalStateException("wedid.auth.refresh-token-ttl은 0보다 커야 합니다.");
        }
        frontendBaseUrl = frontendBaseUrl.endsWith("/")
                ? frontendBaseUrl.substring(0, frontendBaseUrl.length() - 1)
                : frontendBaseUrl;
    }
}
