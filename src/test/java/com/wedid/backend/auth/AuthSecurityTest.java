package com.wedid.backend.auth;

import com.wedid.backend.auth.controller.AuthController;
import com.wedid.backend.auth.oauth.OAuthLoginFailureHandler;
import com.wedid.backend.auth.oauth.OAuthLoginSuccessHandler;
import com.wedid.backend.auth.service.InvalidRefreshTokenException;
import com.wedid.backend.auth.service.OAuthLoginService;
import com.wedid.backend.auth.service.RefreshTokenService;
import com.wedid.backend.auth.service.RefreshTokenService.RotatedRefreshToken;
import com.wedid.backend.auth.token.AccessTokenProvider;
import com.wedid.backend.auth.web.AuthCookieManager;
import com.wedid.backend.global.config.ClockConfig;
import com.wedid.backend.global.config.CorsConfig;
import com.wedid.backend.global.security.SecurityConfig;
import com.wedid.backend.user.entity.User;
import com.wedid.backend.user.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import({
        SecurityConfig.class,
        CorsConfig.class,
        ClockConfig.class,
        AccessTokenProvider.class,
        AuthCookieManager.class,
        OAuthLoginSuccessHandler.class,
        OAuthLoginFailureHandler.class
})
@TestPropertySource(properties = {
        "spring.security.oauth2.client.registration.kakao.client-id=test-kakao-id",
        "spring.security.oauth2.client.registration.kakao.client-secret=test-kakao-secret",
        "spring.security.oauth2.client.registration.google.client-id=test-google-id",
        "spring.security.oauth2.client.registration.google.client-secret=test-google-secret",
        "wedid.auth.jwt-secret=test-secret-test-secret-test-secret-0123",
        "wedid.cors.allowed-origins=http://localhost:3000"
})
class AuthSecurityTest {

    private static final UUID USER_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AccessTokenProvider accessTokenProvider;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private RefreshTokenService refreshTokenService;

    @MockitoBean
    private OAuthLoginService oauthLoginService;

    @Test
    void 비로그인_상태_확인은_401이_아니라_authenticated_false() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(false))
                .andExpect(jsonPath("$.user").doesNotExist());
    }

    @Test
    void 유효한_액세스_토큰_쿠키면_회원_정보를_돌려준다() throws Exception {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user("신랑", "groom@kakao.com")));

        mockMvc.perform(get("/api/v1/auth/me").cookie(accessCookie(accessTokenProvider.issue(USER_ID))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.user.displayName").value("신랑"))
                .andExpect(jsonPath("$.user.email").value("groom@kakao.com"));
    }

    @Test
    void 위조된_토큰_쿠키가_있어도_비회원_API는_막지_않는다() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me").cookie(accessCookie("forged.token.value")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(false));
    }

    @Test
    void 인증이_필요한_API는_로그인_페이지_리다이렉트가_아니라_401_JSON() throws Exception {
        mockMvc.perform(get("/api/v1/plans/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("로그인이 필요합니다."));

        mockMvc.perform(get("/api/v1/plans/me").cookie(accessCookie("forged.token.value")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 예식장_후보_목록과_상세는_로그인이_필요하다() throws Exception {
        mockMvc.perform(get("/api/v1/halls"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/halls/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 로그인_시작은_카카오_인증_화면으로_보내고_돌아갈_경로를_보관한다() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/auth/login/kakao")
                        .param("redirect", "/roadmap?resume=save-plan"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string(HttpHeaders.LOCATION,
                        startsWith("https://kauth.kakao.com/oauth/authorize")))
                .andExpect(header().string(HttpHeaders.LOCATION, containsString("client_id=test-kakao-id")))
                .andExpect(header().string(HttpHeaders.LOCATION,
                        containsString("redirect_uri=http://localhost/api/v1/auth/callback/kakao")))
                .andReturn();

        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        assertThat(session).isNotNull();
        assertThat(session.getAttribute("com.wedid.backend.auth.oauth.LoginRedirectPath"))
                .isEqualTo("/roadmap?resume=save-plan");
    }

    @Test
    void 구글_로그인_시작은_구글_인증_화면으로_보낸다() throws Exception {
        mockMvc.perform(get("/api/v1/auth/login/google"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string(HttpHeaders.LOCATION,
                        startsWith("https://accounts.google.com/o/oauth2/v2/auth")));
    }

    @Test
    void 등록되지_않은_공급자는_404() throws Exception {
        mockMvc.perform(get("/api/v1/auth/login/naver"))
                .andExpect(status().isNotFound());
    }

    @Test
    void 리프레시_토큰으로_두_토큰을_새로_발급한다() throws Exception {
        when(refreshTokenService.rotate("old-refresh")).thenReturn(new RotatedRefreshToken(USER_ID, "new-refresh"));

        MvcResult result = mockMvc.perform(post("/api/v1/auth/refresh").cookie(refreshCookie("old-refresh")))
                .andExpect(status().isNoContent())
                .andReturn();

        assertThat(result.getResponse().getHeaders(HttpHeaders.SET_COOKIE))
                .anySatisfy(cookie -> assertThat(cookie)
                        .startsWith(AuthCookieManager.ACCESS_TOKEN_COOKIE + "=ey")
                        .contains("HttpOnly", "SameSite=Lax", "Path=/"))
                .anySatisfy(cookie -> assertThat(cookie)
                        .startsWith(AuthCookieManager.REFRESH_TOKEN_COOKIE + "=new-refresh")
                        .contains("HttpOnly", "SameSite=Lax", "Path=/api/v1/auth"));
    }

    @Test
    void 리프레시_토큰이_없거나_유효하지_않으면_쿠키를_지우고_401() throws Exception {
        when(refreshTokenService.rotate("bad")).thenThrow(new InvalidRefreshTokenException());

        mockMvc.perform(post("/api/v1/auth/refresh"))
                .andExpect(status().isUnauthorized());

        MvcResult result = mockMvc.perform(post("/api/v1/auth/refresh").cookie(refreshCookie("bad")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").exists())
                .andReturn();
        assertThat(result.getResponse().getHeaders(HttpHeaders.SET_COOKIE))
                .allSatisfy(cookie -> assertThat(cookie).contains("Max-Age=0"));
    }

    @Test
    void 로그아웃하면_리프레시_토큰을_폐기하고_쿠키를_지운다() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/logout")
                        .cookie(accessCookie(accessTokenProvider.issue(USER_ID)), refreshCookie("mine")))
                .andExpect(status().isNoContent())
                .andReturn();

        verify(refreshTokenService).revoke("mine");
        assertThat(result.getResponse().getHeaders(HttpHeaders.SET_COOKIE))
                .hasSize(2)
                .allSatisfy(cookie -> assertThat(cookie).contains("Max-Age=0"));
    }

    private static Cookie accessCookie(String value) {
        return new Cookie(AuthCookieManager.ACCESS_TOKEN_COOKIE, value);
    }

    private static Cookie refreshCookie(String value) {
        return new Cookie(AuthCookieManager.REFRESH_TOKEN_COOKIE, value);
    }

    private static User user(String displayName, String email) {
        User user = User.create(displayName, email, OffsetDateTime.now());
        org.springframework.test.util.ReflectionTestUtils.setField(user, "id", USER_ID);
        return user;
    }
}
