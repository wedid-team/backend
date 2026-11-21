package com.wedid.backend.auth.oauth;

import com.wedid.backend.auth.config.AuthProperties;
import com.wedid.backend.auth.service.OAuthLoginService;
import com.wedid.backend.auth.service.RefreshTokenService;
import com.wedid.backend.auth.token.AccessTokenProvider;
import com.wedid.backend.auth.web.AuthCookieManager;
import com.wedid.backend.user.entity.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 카카오·구글 인증 성공 후: 회원 조회·생성 → 토큰 쿠키 발급 → 원래 프론트엔드 화면으로 리다이렉트.
 * 회원 처리 중 오류가 나면 로그인 실패(loginError=failed)로 돌려보낸다.
 */
@Slf4j
@Component
public class OAuthLoginSuccessHandler implements AuthenticationSuccessHandler {

    private final OAuthLoginService oauthLoginService;
    private final AccessTokenProvider accessTokenProvider;
    private final RefreshTokenService refreshTokenService;
    private final AuthCookieManager cookieManager;
    private final AuthProperties properties;

    public OAuthLoginSuccessHandler(
            OAuthLoginService oauthLoginService,
            AccessTokenProvider accessTokenProvider,
            RefreshTokenService refreshTokenService,
            AuthCookieManager cookieManager,
            AuthProperties properties
    ) {
        this.oauthLoginService = oauthLoginService;
        this.accessTokenProvider = accessTokenProvider;
        this.refreshTokenService = refreshTokenService;
        this.cookieManager = cookieManager;
        this.properties = properties;
    }

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) throws IOException {
        String redirectPath = LoginRedirectPath.consume(request);
        // 인증 상태는 토큰 쿠키로만 유지한다. OAuth 인증 객체가 남지 않도록 비운다.
        SecurityContextHolder.clearContext();

        try {
            OAuth2AuthenticationToken token = (OAuth2AuthenticationToken) authentication;
            OAuthUserProfile profile = OAuthUserProfile.of(
                    token.getAuthorizedClientRegistrationId(),
                    token.getPrincipal().getAttributes()
            );
            User user = loginOrRegister(profile);

            cookieManager.writeTokens(
                    response,
                    accessTokenProvider.issue(user.getId()),
                    refreshTokenService.issue(user.getId())
            );
            response.sendRedirect(LoginRedirectPath.toFrontendUrl(properties, redirectPath, null));
        } catch (RuntimeException e) {
            log.error("소셜 로그인 후 회원 처리에 실패했습니다.", e);
            response.sendRedirect(LoginRedirectPath.toFrontendUrl(
                    properties, redirectPath, OAuthLoginFailureHandler.ERROR_FAILED));
        }
    }

    /** 같은 계정의 첫 로그인이 동시에 들어와 UNIQUE 위반이 나면, 먼저 만들어진 회원으로 한 번 더 조회한다. */
    private User loginOrRegister(OAuthUserProfile profile) {
        try {
            return oauthLoginService.loginOrRegister(profile);
        } catch (DataIntegrityViolationException e) {
            return oauthLoginService.loginOrRegister(profile);
        }
    }
}
