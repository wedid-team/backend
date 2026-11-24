package com.wedid.backend.auth.oauth;

import com.wedid.backend.auth.config.AuthProperties;
import com.wedid.backend.auth.service.OAuthLoginService;
import com.wedid.backend.auth.service.RefreshTokenService;
import com.wedid.backend.auth.token.AccessTokenProvider;
import com.wedid.backend.auth.web.AuthCookieManager;
import com.wedid.backend.user.entity.OAuthProvider;
import com.wedid.backend.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OAuthLoginHandlersTest {

    private final AuthProperties properties = new AuthProperties("http://localhost:3000", "a".repeat(32),
            Duration.ofMinutes(30), Duration.ofDays(14), false);

    private OAuthLoginService loginService;
    private RefreshTokenService refreshTokenService;
    private OAuthLoginSuccessHandler successHandler;
    private OAuthLoginFailureHandler failureHandler;
    private MockHttpServletRequest request;
    private MockHttpSession session;

    @BeforeEach
    void setUp() {
        loginService = mock(OAuthLoginService.class);
        refreshTokenService = mock(RefreshTokenService.class);
        successHandler = new OAuthLoginSuccessHandler(
                loginService,
                new AccessTokenProvider(properties, Clock.systemUTC()),
                refreshTokenService,
                new AuthCookieManager(properties),
                properties
        );
        failureHandler = new OAuthLoginFailureHandler(properties);

        request = new MockHttpServletRequest("GET", "/api/v1/auth/callback/kakao");
        session = new MockHttpSession();
        session.setAttribute(LoginRedirectPath.SESSION_ATTRIBUTE, "/vendors/halls?from=roadmap");
        request.setSession(session);
    }

    @Test
    void 로그인_성공하면_회원을_만들고_토큰_쿠키와_함께_원래_화면으로_보낸다() throws Exception {
        User user = User.create("신랑", null, OffsetDateTime.now());
        when(loginService.loginOrRegister(any())).thenReturn(user);
        when(refreshTokenService.issue(user.getId())).thenReturn("refresh-raw");
        MockHttpServletResponse response = new MockHttpServletResponse();

        successHandler.onAuthenticationSuccess(request, response, kakaoAuthentication());

        verify(loginService).loginOrRegister(new OAuthUserProfile(OAuthProvider.KAKAO, "4321", "신랑", null));
        assertThat(response.getRedirectedUrl()).isEqualTo("http://localhost:3000/vendors/halls?from=roadmap");
        assertThat(response.getHeaders(HttpHeaders.SET_COOKIE))
                .anySatisfy(c -> assertThat(c).startsWith(AuthCookieManager.ACCESS_TOKEN_COOKIE + "="))
                .anySatisfy(c -> assertThat(c).startsWith(AuthCookieManager.REFRESH_TOKEN_COOKIE + "=refresh-raw"));
        assertThat(session.isInvalid()).isTrue();
    }

    @Test
    void 동시_첫_로그인으로_UNIQUE_위반이_나면_한_번_더_조회한다() throws Exception {
        User user = User.create("신랑", null, OffsetDateTime.now());
        when(loginService.loginOrRegister(any()))
                .thenThrow(new DataIntegrityViolationException("duplicate"))
                .thenReturn(user);
        MockHttpServletResponse response = new MockHttpServletResponse();

        successHandler.onAuthenticationSuccess(request, response, kakaoAuthentication());

        verify(loginService, times(2)).loginOrRegister(any());
        assertThat(response.getRedirectedUrl()).isEqualTo("http://localhost:3000/vendors/halls?from=roadmap");
    }

    @Test
    void 회원_처리_중_오류가_나면_토큰_없이_실패로_돌려보낸다() throws Exception {
        when(loginService.loginOrRegister(any())).thenThrow(new IllegalStateException("db down"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        successHandler.onAuthenticationSuccess(request, response, kakaoAuthentication());

        assertThat(response.getRedirectedUrl())
                .isEqualTo("http://localhost:3000/vendors/halls?from=roadmap&loginError=failed");
        assertThat(response.getHeaders(HttpHeaders.SET_COOKIE)).isEmpty();
    }

    @Test
    void 사용자가_동의_화면에서_취소하면_cancelled() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        failureHandler.onAuthenticationFailure(request, response,
                new OAuth2AuthenticationException(new OAuth2Error("access_denied")));

        assertThat(response.getRedirectedUrl())
                .isEqualTo("http://localhost:3000/vendors/halls?from=roadmap&loginError=cancelled");
        assertThat(session.isInvalid()).isTrue();
    }

    @Test
    void 그_밖의_실패는_failed() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        failureHandler.onAuthenticationFailure(request, response,
                new OAuth2AuthenticationException(new OAuth2Error("invalid_token_response")));

        assertThat(response.getRedirectedUrl())
                .isEqualTo("http://localhost:3000/vendors/halls?from=roadmap&loginError=failed");
    }

    private static OAuth2AuthenticationToken kakaoAuthentication() {
        Map<String, Object> attributes = Map.of(
                "id", 4321L,
                "kakao_account", Map.of("profile", Map.of("nickname", "신랑"))
        );
        DefaultOAuth2User principal = new DefaultOAuth2User(
                List.of(new SimpleGrantedAuthority("OAUTH2_USER")), attributes, "id");
        return new OAuth2AuthenticationToken(principal, principal.getAuthorities(), "kakao");
    }
}
