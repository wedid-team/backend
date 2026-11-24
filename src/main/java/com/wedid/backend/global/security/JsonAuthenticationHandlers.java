package com.wedid.backend.global.security;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 인증·인가 실패를 로그인 페이지 리다이렉트 대신 공통 오류 응답(ErrorResponse 형태)으로 돌려준다.
 * 프론트엔드는 401을 받으면 POST /api/v1/auth/refresh를 한 번 시도하고, 그래도 401이면 로그인 모달을 띄운다.
 */
final class JsonAuthenticationHandlers {

    private JsonAuthenticationHandlers() {
    }

    static AuthenticationEntryPoint unauthorized() {
        return (request, response, exception) ->
                write(response, HttpServletResponse.SC_UNAUTHORIZED, "로그인이 필요합니다.");
    }

    static AccessDeniedHandler forbidden() {
        return (request, response, exception) ->
                write(response, HttpServletResponse.SC_FORBIDDEN, "접근 권한이 없습니다.");
    }

    private static void write(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        // 메시지는 위의 고정 문자열뿐이라 따로 JSON 이스케이프하지 않는다.
        response.getWriter().write("{\"message\":\"" + message + "\"}");
    }
}
