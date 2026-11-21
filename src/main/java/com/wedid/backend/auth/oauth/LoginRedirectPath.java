package com.wedid.backend.auth.oauth;

import com.wedid.backend.auth.config.AuthProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * 로그인 후 돌아갈 프론트엔드 경로 (C-03 · C-09 · R-12: 로그인 성공 시 누른 동작으로 이어서 실행).
 *
 * <p>프론트엔드가 {@code /api/v1/auth/login/{provider}?redirect=/roadmap?resume=save-plan} 처럼
 * 돌아올 경로를 넘기면, OAuth 진행 동안 세션에 보관했다가 로그인 성공·실패 후 그 경로로 보낸다.
 * 외부 사이트로 보내는 오픈 리다이렉트를 막기 위해 같은 사이트 안의 상대 경로만 허용한다.
 */
public final class LoginRedirectPath {

    public static final String PARAMETER = "redirect";
    static final String SESSION_ATTRIBUTE = LoginRedirectPath.class.getName();

    private static final String DEFAULT_PATH = "/";
    private static final int MAX_LENGTH = 2048;

    private LoginRedirectPath() {
    }

    /** "/"로 시작하는 상대 경로만 허용한다. "//host", "/\host", 제어 문자가 들어간 값은 기본 경로로 바꾼다. */
    public static String sanitize(String rawPath) {
        if (rawPath == null || rawPath.isBlank() || rawPath.length() > MAX_LENGTH) {
            return DEFAULT_PATH;
        }
        if (!rawPath.startsWith("/") || rawPath.startsWith("//") || rawPath.contains("\\")) {
            return DEFAULT_PATH;
        }
        for (int i = 0; i < rawPath.length(); i++) {
            if (Character.isISOControl(rawPath.charAt(i)) || Character.isWhitespace(rawPath.charAt(i))) {
                return DEFAULT_PATH;
            }
        }
        return rawPath;
    }

    static void save(HttpServletRequest request) {
        String path = sanitize(request.getParameter(PARAMETER));
        request.getSession().setAttribute(SESSION_ATTRIBUTE, path);
    }

    /**
     * 보관한 경로를 꺼내고 OAuth 진행에만 쓰던 세션을 정리한다.
     * 인증 상태는 토큰 쿠키로만 유지하므로 로그인이 끝나면 세션이 필요 없다.
     */
    static String consume(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return DEFAULT_PATH;
        }
        Object saved = session.getAttribute(SESSION_ATTRIBUTE);
        session.invalidate();
        return saved instanceof String path ? sanitize(path) : DEFAULT_PATH;
    }

    static String toFrontendUrl(AuthProperties properties, String path, String loginError) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(properties.frontendBaseUrl() + path);
        if (loginError != null) {
            builder.replaceQueryParam("loginError", loginError);
        }
        return builder.build().toUriString();
    }
}
