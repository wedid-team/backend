package com.wedid.backend.invitation;

import com.wedid.backend.auth.oauth.OAuthLoginFailureHandler;
import com.wedid.backend.auth.oauth.OAuthLoginSuccessHandler;
import com.wedid.backend.auth.service.OAuthLoginService;
import com.wedid.backend.auth.service.RefreshTokenService;
import com.wedid.backend.auth.token.AccessTokenProvider;
import com.wedid.backend.auth.web.AuthCookieManager;
import com.wedid.backend.global.config.ClockConfig;
import com.wedid.backend.global.config.CorsConfig;
import com.wedid.backend.global.security.SecurityConfig;
import com.wedid.backend.invitation.controller.InvitationController;
import com.wedid.backend.invitation.dto.InvitationAcceptResponse;
import com.wedid.backend.invitation.dto.InvitationCreateResponse;
import com.wedid.backend.invitation.dto.InvitationPreviewResponse;
import com.wedid.backend.invitation.dto.InvitationPreviewResponse.Status;
import com.wedid.backend.invitation.dto.InvitationPreviewResponse.ViewerPlan;
import com.wedid.backend.invitation.service.InvitationConflictException;
import com.wedid.backend.invitation.service.InvitationExpiredException;
import com.wedid.backend.invitation.service.InvitationService;
import com.wedid.backend.plan.service.PlanNotFoundException;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InvitationController.class)
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
class InvitationControllerTest {

    private static final UUID USER_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final String TOKEN = "abc_DEF-123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AccessTokenProvider accessTokenProvider;

    @MockitoBean
    private InvitationService invitationService;

    @MockitoBean
    private RefreshTokenService refreshTokenService;

    @MockitoBean
    private OAuthLoginService oauthLoginService;

    @Test
    void 발급과_합류는_로그인이_필요하다() throws Exception {
        mockMvc.perform(post("/api/v1/invitations"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/invitations/" + TOKEN + "/accept"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(invitationService);
    }

    @Test
    void 초대_링크를_발급한다() throws Exception {
        when(invitationService.create(USER_ID)).thenReturn(
                new InvitationCreateResponse(TOKEN, OffsetDateTime.parse("2026-12-01T03:00:00Z")));

        mockMvc.perform(post("/api/v1/invitations").cookie(loggedIn()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").value(TOKEN))
                .andExpect(jsonPath("$.expiresAt").exists());
    }

    @Test
    void 짝꿍이_있으면_발급은_409다() throws Exception {
        when(invitationService.create(USER_ID)).thenThrow(InvitationConflictException.planAlreadyConnected());

        mockMvc.perform(post("/api/v1/invitations").cookie(loggedIn()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PLAN_ALREADY_CONNECTED"))
                .andExpect(jsonPath("$.message").value("이미 짝꿍과 연결된 계획이에요."));
    }

    @Test
    void 분기용_코드가_없는_오류는_message만_준다() throws Exception {
        when(invitationService.create(USER_ID)).thenThrow(new PlanNotFoundException());

        mockMvc.perform(post("/api/v1/invitations").cookie(loggedIn()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("저장된 계획이 없어요."))
                .andExpect(jsonPath("$.code").doesNotExist());
    }

    @Test
    void 링크_확인은_로그인_없이_된다() throws Exception {
        when(invitationService.preview(eq(TOKEN), isNull())).thenReturn(
                new InvitationPreviewResponse(Status.VALID, "신랑", OffsetDateTime.parse("2026-12-01T03:00:00Z"), null));

        mockMvc.perform(get("/api/v1/invitations/" + TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VALID"))
                .andExpect(jsonPath("$.inviterName").value("신랑"))
                .andExpect(jsonPath("$.viewerPlan").doesNotExist());
    }

    @Test
    void 로그인했으면_링크_확인에_내_회원_ID를_넘긴다() throws Exception {
        when(invitationService.preview(TOKEN, USER_ID)).thenReturn(
                new InvitationPreviewResponse(Status.VALID, "신랑", null, ViewerPlan.HAS_PLAN));

        mockMvc.perform(get("/api/v1/invitations/" + TOKEN).cookie(loggedIn()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.viewerPlan").value("HAS_PLAN"));
    }

    @Test
    void 만료된_링크는_410이다() throws Exception {
        when(invitationService.preview(anyString(), any())).thenThrow(new InvitationExpiredException());
        when(invitationService.accept(anyString(), any(), anyBoolean())).thenThrow(new InvitationExpiredException());

        mockMvc.perform(get("/api/v1/invitations/" + TOKEN))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("INVITATION_EXPIRED"))
                .andExpect(jsonPath("$.message").value("초대 링크가 만료됐어요."));
        mockMvc.perform(post("/api/v1/invitations/" + TOKEN + "/accept").cookie(loggedIn()))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("INVITATION_EXPIRED"));
    }

    @Test
    void 본문_없이_합류하면_교체_동의는_false다() throws Exception {
        UUID planId = UUID.randomUUID();
        when(invitationService.accept(TOKEN, USER_ID, false)).thenReturn(
                new InvitationAcceptResponse(planId, "PARTNER", OffsetDateTime.parse("2026-11-24T03:00:00Z"), null));

        mockMvc.perform(post("/api/v1/invitations/" + TOKEN + "/accept").cookie(loggedIn()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.planId").value(planId.toString()))
                .andExpect(jsonPath("$.role").value("PARTNER"));
        verify(invitationService).accept(TOKEN, USER_ID, false);
    }

    @Test
    void 교체에_동의하면_true로_넘기고_동의가_없으면_409다() throws Exception {
        when(invitationService.accept(TOKEN, USER_ID, false))
                .thenThrow(InvitationConflictException.replaceConfirmationRequired());
        when(invitationService.accept(TOKEN, USER_ID, true)).thenReturn(
                new InvitationAcceptResponse(UUID.randomUUID(), "PARTNER", null, UUID.randomUUID()));

        mockMvc.perform(post("/api/v1/invitations/" + TOKEN + "/accept").cookie(loggedIn())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REPLACE_CONFIRMATION_REQUIRED"))
                .andExpect(jsonPath("$.message").value("합류하면 내 계획은 사라져요."));
        mockMvc.perform(post("/api/v1/invitations/" + TOKEN + "/accept").cookie(loggedIn())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"replaceMyPlan\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.replacedPlanId").exists());
    }

    @Test
    void 합류_충돌은_코드로_구분된다() throws Exception {
        when(invitationService.accept(TOKEN, USER_ID, false)).thenThrow(InvitationConflictException.alreadyMember());
        mockMvc.perform(post("/api/v1/invitations/" + TOKEN + "/accept").cookie(loggedIn()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_MEMBER"));

        doThrow(InvitationConflictException.viewerAlreadyConnected())
                .when(invitationService).accept(TOKEN, USER_ID, false);
        mockMvc.perform(post("/api/v1/invitations/" + TOKEN + "/accept").cookie(loggedIn()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VIEWER_ALREADY_CONNECTED"))
                .andExpect(jsonPath("$.message").value("이미 짝꿍과 연결되어 있어요. 연결을 해제한 뒤 합류할 수 있어요."));
    }

    private Cookie loggedIn() {
        return new Cookie(AuthCookieManager.ACCESS_TOKEN_COOKIE, accessTokenProvider.issue(USER_ID));
    }
}
