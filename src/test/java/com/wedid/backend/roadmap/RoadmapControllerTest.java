package com.wedid.backend.roadmap;

import com.wedid.backend.auth.oauth.OAuthLoginFailureHandler;
import com.wedid.backend.auth.oauth.OAuthLoginSuccessHandler;
import com.wedid.backend.auth.service.OAuthLoginService;
import com.wedid.backend.auth.service.RefreshTokenService;
import com.wedid.backend.auth.token.AccessTokenProvider;
import com.wedid.backend.auth.web.AuthCookieManager;
import com.wedid.backend.global.config.ClockConfig;
import com.wedid.backend.global.config.CorsConfig;
import com.wedid.backend.global.security.SecurityConfig;
import com.wedid.backend.plan.service.PlanNotFoundException;
import com.wedid.backend.roadmap.controller.RoadmapController;
import com.wedid.backend.roadmap.dto.BudgetStatusResponse;
import com.wedid.backend.roadmap.dto.RoadmapStepResponse;
import com.wedid.backend.roadmap.dto.TodoResponse;
import com.wedid.backend.roadmap.dto.TodoUpdateRequest;
import com.wedid.backend.roadmap.service.RoadmapItemNotFoundException;
import com.wedid.backend.roadmap.service.RoadmapService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RoadmapController.class)
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
class RoadmapControllerTest {

    private static final UUID USER_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final UUID ID = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AccessTokenProvider accessTokenProvider;

    @MockitoBean
    private RoadmapService roadmapService;

    @MockitoBean
    private RefreshTokenService refreshTokenService;

    @MockitoBean
    private OAuthLoginService oauthLoginService;

    @Test
    void 로그인하지_않으면_401이다() throws Exception {
        mockMvc.perform(patch("/api/v1/plans/me/tasks/" + ID).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"completed\":true}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(patch("/api/v1/plans/me/roadmap/steps/" + ID).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"completed\":true}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/v1/plans/me/contracts/" + ID).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"actualAmount\":1}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/plans/me/budget-status"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(roadmapService);
    }

    @Test
    void 할_일_체크와_담당을_바꾼다() throws Exception {
        when(roadmapService.updateTodo(eq(USER_ID), eq(ID), any())).thenReturn(new TodoResponse(
                ID, UUID.randomUUID(), "할 일", true, null, "PARTNER", UUID.randomUUID(), USER_ID, null));

        mockMvc.perform(patch("/api/v1/plans/me/tasks/" + ID).cookie(loggedIn())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"completed\":true,\"assignee\":\"PARTNER\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taskId").value(ID.toString()))
                .andExpect(jsonPath("$.assignee").value("PARTNER"));

        verify(roadmapService).updateTodo(USER_ID, ID, new TodoUpdateRequest(true, "PARTNER"));
    }

    @Test
    void 할_일_요청이_잘못되면_400이고_서비스까지_가지_않는다() throws Exception {
        mockMvc.perform(patch("/api/v1/plans/me/tasks/" + ID).cookie(loggedIn())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("바꿀 항목을 보내주세요."));
        mockMvc.perform(patch("/api/v1/plans/me/tasks/" + ID).cookie(loggedIn())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"assignee\":\"MOM\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("올바른 담당을 선택해주세요."));
        mockMvc.perform(patch("/api/v1/plans/me/tasks/not-a-uuid").cookie(loggedIn())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"completed\":true}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(roadmapService);
    }

    @Test
    void 내_계획에_없는_단계_할_일은_404다() throws Exception {
        when(roadmapService.updateTodo(eq(USER_ID), eq(ID), any())).thenThrow(RoadmapItemNotFoundException.todo());
        when(roadmapService.updateStepCompletion(eq(USER_ID), eq(ID), anyBoolean()))
                .thenThrow(RoadmapItemNotFoundException.step());

        mockMvc.perform(patch("/api/v1/plans/me/tasks/" + ID).cookie(loggedIn())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"completed\":true}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("할 일을 찾을 수 없어요."));
        mockMvc.perform(patch("/api/v1/plans/me/roadmap/steps/" + ID).cookie(loggedIn())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"completed\":true}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("로드맵 단계를 찾을 수 없어요."));
    }

    @Test
    void 금액_없이_금액_단계를_완료하면_400이다() throws Exception {
        when(roadmapService.updateStepCompletion(USER_ID, ID, true))
                .thenThrow(new IllegalArgumentException("실제 계약 금액을 입력해야 단계를 완료할 수 있어요."));

        mockMvc.perform(patch("/api/v1/plans/me/roadmap/steps/" + ID).cookie(loggedIn())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"completed\":true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("실제 계약 금액을 입력해야 단계를 완료할 수 있어요."));

        mockMvc.perform(patch("/api/v1/plans/me/roadmap/steps/" + ID).cookie(loggedIn())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("완료 여부를 선택해주세요."));
    }

    @Test
    void 실제_계약_금액을_원_단위로_받는다() throws Exception {
        when(roadmapService.recordContractAmount(USER_ID, ID, 25_000_000L)).thenReturn(new RoadmapStepResponse(
                ID, "VENUE_CONTRACT", "VENUE", null, new BigDecimal("25000000"), false, null, null, USER_ID, null));

        mockMvc.perform(put("/api/v1/plans/me/contracts/" + ID).cookie(loggedIn())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"actualAmount\":25000000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.actualAmount").value(25000000));
    }

    @Test
    void 실제_계약_금액이_없거나_음수면_400이다() throws Exception {
        mockMvc.perform(put("/api/v1/plans/me/contracts/" + ID).cookie(loggedIn())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("실제 계약 금액을 입력해주세요."));
        mockMvc.perform(put("/api/v1/plans/me/contracts/" + ID).cookie(loggedIn())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"actualAmount\":-1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("실제 계약 금액은 0원 이상이어야 해요."));
        mockMvc.perform(put("/api/v1/plans/me/contracts/" + ID).cookie(loggedIn())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"actualAmount\":999990001}"))
                .andExpect(status().isBadRequest());
        verify(roadmapService, org.mockito.Mockito.never()).recordContractAmount(any(), any(), anyLong());
    }

    @Test
    void 남은_예산_판정을_돌려준다() throws Exception {
        when(roadmapService.getBudgetStatus(USER_ID)).thenReturn(new BudgetStatusResponse(
                new BigDecimal("30000000"), new BigDecimal("25000000"), new BigDecimal("5000000"), BigDecimal.ZERO,
                null, null, BudgetStatusResponse.SkipReason.ESTIMATE_PENDING, false, false,
                List.of(new BudgetStatusResponse.AmountStepStatus(ID, "VENUE_CONTRACT", "VENUE", false,
                        new BudgetStatusResponse.Amount(new BigDecimal("20000000"), new BigDecimal("30000000")),
                        null, "REMAINING_ESTIMATE"))));

        mockMvc.perform(get("/api/v1/plans/me/budget-status").cookie(loggedIn()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.remainingBudget").value(5000000))
                .andExpect(jsonPath("$.remainingEstimate").doesNotExist())
                .andExpect(jsonPath("$.verdict").doesNotExist())
                .andExpect(jsonPath("$.verdictSkipReason").value("ESTIMATE_PENDING"))
                .andExpect(jsonPath("$.budget").value(30000000))
                .andExpect(jsonPath("$.spentAmount").value(25000000))
                .andExpect(jsonPath("$.amountSteps[0].code").value("VENUE_CONTRACT"))
                .andExpect(jsonPath("$.amountSteps[0].estimated.min").value(20000000))
                .andExpect(jsonPath("$.amountSteps[0].estimated.max").value(30000000));
    }

    @Test
    void 계획이_없으면_404다() throws Exception {
        when(roadmapService.getBudgetStatus(USER_ID)).thenThrow(new PlanNotFoundException());

        mockMvc.perform(get("/api/v1/plans/me/budget-status").cookie(loggedIn()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("저장된 계획이 없어요."));
    }

    private Cookie loggedIn() {
        return new Cookie(AuthCookieManager.ACCESS_TOKEN_COOKIE, accessTokenProvider.issue(USER_ID));
    }
}
