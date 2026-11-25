package com.wedid.backend.auth.oauth;

import com.wedid.backend.auth.config.AuthProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 소셜 로그인 실패 시 원래 화면으로 돌려보내며 loginError 쿼리로 원인을 알린다.
 * - cancelled: 사용자가 동의 화면에서 취소 (C-03: 모달을 닫은 것과 같이 변화 없음)
 * - failed: 그 밖의 실패 (U-04 로그인 실패 상태)
 */
@Slf4j
@Component
public class OAuthLoginFailureHandler implements AuthenticationFailureHandler {

    public static final String ERROR_CANCELLED = "cancelled";
    public static final String ERROR_FAILED = "failed";

    private final AuthProperties properties;

    public OAuthLoginFailureHandler(AuthProperties properties) {
        this.properties = properties;
    }

    @Override
    public void onAuthenticationFailure(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception
    ) throws IOException {
        String redirectPath = LoginRedirectPath.consume(request);
        String error = isCancelled(exception) ? ERROR_CANCELLED : ERROR_FAILED;

        if (ERROR_FAILED.equals(error)) {
            log.warn("소셜 로그인에 실패했습니다: {}", exception.getMessage());
        }
        response.sendRedirect(LoginRedirectPath.toFrontendUrl(properties, redirectPath, error));
    }

    private boolean isCancelled(AuthenticationException exception) {
        return exception instanceof OAuth2AuthenticationException oauth2Exception
                && OAuth2ErrorCodes.ACCESS_DENIED.equals(oauth2Exception.getError().getErrorCode());
    }
}
