package com.wedid.backend.plan;

import com.wedid.backend.auth.oauth.OAuthLoginFailureHandler;
import com.wedid.backend.auth.oauth.OAuthLoginSuccessHandler;
import com.wedid.backend.auth.service.OAuthLoginService;
import com.wedid.backend.auth.service.RefreshTokenService;
import com.wedid.backend.auth.token.AccessTokenProvider;
import com.wedid.backend.auth.web.AuthCookieManager;
import com.wedid.backend.estimate.region.RegionRepository;
import com.wedid.backend.global.config.ClockConfig;
import com.wedid.backend.global.config.CorsConfig;
import com.wedid.backend.global.security.SecurityConfig;
import com.wedid.backend.plan.controller.PlanController;
import com.wedid.backend.plan.dto.PlanRequest;
import com.wedid.backend.plan.dto.PlanResponse;
import com.wedid.backend.plan.service.PlanAlreadyExistsException;
import com.wedid.backend.plan.service.PlanNotFoundException;
import com.wedid.backend.plan.service.PlanService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PlanController.class)
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
class PlanControllerTest {

    private static final UUID USER_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");

    private static final String VALID_BODY = """
            {
              "regionCode": "SEOUL_GANGNAM",
              "guestRangeCode": "FROM_150_TO_200",
              "weddingDateType": "MONTH",
              "weddingYear": 2027,
              "weddingMonth": 10,
              "budget": 30000000,
              "moods": ["BRIGHT"],
              "sdmBandCode": "STANDARD"
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AccessTokenProvider accessTokenProvider;

    @MockitoBean
    private PlanService planService;

    @MockitoBean
    private RefreshTokenService refreshTokenService;

    @MockitoBean
    private OAuthLoginService oauthLoginService;

    // 지역 검증(@KnownRegion)이 regions 테이블을 보므로, DB에 SEOUL_GANGNAM만 있다고 둔다
    @MockitoBean
    private RegionRepository regionRepository;

    @BeforeEach
    void regionsInDatabase() {
        when(regionRepository.existsByCode("SEOUL_GANGNAM")).thenReturn(true);
    }

    @Test
    void 로그인하지_않으면_계획_API는_401이다() throws Exception {
        mockMvc.perform(post("/api/v1/plans").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/plans/me"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/v1/plans/me").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(planService);
    }

    @Test
    void 계획을_저장하면_201과_내_계획_주소를_돌려준다() throws Exception {
        when(planService.create(eq(USER_ID), any())).thenReturn(response());

        mockMvc.perform(post("/api/v1/plans").cookie(loggedIn())
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/plans/me"))
                .andExpect(jsonPath("$.role").value("OWNER"));

        ArgumentCaptor<PlanRequest> captor = ArgumentCaptor.forClass(PlanRequest.class);
        verify(planService).create(eq(USER_ID), captor.capture());
        assertThat(captor.getValue().weddingMonth()).isEqualTo(10);
        assertThat(captor.getValue().sdmBandCode()).isEqualTo("STANDARD");
    }

    @Test
    void 이미_계획이_있으면_409로_알린다() throws Exception {
        when(planService.create(eq(USER_ID), any())).thenThrow(new PlanAlreadyExistsException());

        mockMvc.perform(post("/api/v1/plans").cookie(loggedIn())
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("이미 저장된 계획이 있어요."));
    }

    @Test
    void 선택_범위를_벗어난_예식_시기는_400이다() throws Exception {
        when(planService.create(eq(USER_ID), any()))
                .thenThrow(new IllegalArgumentException("예식 시기는 이번 달부터 2년 뒤까지 선택할 수 있어요."));

        mockMvc.perform(post("/api/v1/plans").cookie(loggedIn())
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("예식 시기는 이번 달부터 2년 뒤까지 선택할 수 있어요."));
    }

    @Test
    void 계획이_없으면_404다() throws Exception {
        when(planService.getMine(USER_ID)).thenThrow(new PlanNotFoundException());

        mockMvc.perform(get("/api/v1/plans/me").cookie(loggedIn()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("저장된 계획이 없어요."));
    }

    @Test
    void 조건_덮어쓰기는_PUT이다() throws Exception {
        when(planService.overwriteMine(eq(USER_ID), any())).thenReturn(response());

        mockMvc.perform(put("/api/v1/plans/me").cookie(loggedIn())
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/v1/plans/me").cookie(loggedIn())
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void 예산은_100만_원_이상_99999만_원_이하_만_원_단위만_받는다() throws Exception {
        for (String budget : List.of("999999", "999990001", "30005000", "0")) {
            mockMvc.perform(post("/api/v1/plans").cookie(loggedIn())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_BODY.replace("30000000", budget)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message")
                            .value("예산은 100만 원 이상 99,999만 원 이하, 만 원 단위로 입력해주세요."));
        }

        String noBudget = VALID_BODY.replace("\"budget\": 30000000,", "");
        when(planService.create(eq(USER_ID), any())).thenReturn(response());
        mockMvc.perform(post("/api/v1/plans").cookie(loggedIn())
                        .contentType(MediaType.APPLICATION_JSON).content(noBudget))
                .andExpect(status().isCreated());
    }

    @Test
    void 잘못된_조건은_400이고_서비스까지_가지_않는다() throws Exception {
        mockMvc.perform(post("/api/v1/plans").cookie(loggedIn())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY.replace("\"STANDARD\"", "\"GOLD\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("올바른 스드메 예산대를 선택해주세요."));

        mockMvc.perform(put("/api/v1/plans/me").cookie(loggedIn())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY.replace("\"weddingMonth\": 10", "\"weddingMonth\": 13")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("예식 시기 입력을 확인해주세요."));

        mockMvc.perform(post("/api/v1/plans").cookie(loggedIn())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY.replace("SEOUL_GANGNAM", "MARS")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("올바른 지역을 선택해주세요."));

        // 예전 하드코딩 목록(14개)에는 있었지만 DB regions에 없는 지역도 서비스 전에 막는다
        mockMvc.perform(post("/api/v1/plans").cookie(loggedIn())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY.replace("SEOUL_GANGNAM", "INCHEON")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("올바른 지역을 선택해주세요."));

        verify(planService, never()).create(any(), any());
        verify(planService, never()).overwriteMine(any(), any());
    }

    private Cookie loggedIn() {
        return new Cookie(AuthCookieManager.ACCESS_TOKEN_COOKIE, accessTokenProvider.issue(USER_ID));
    }

    private static PlanResponse response() {
        return new PlanResponse(UUID.randomUUID(), "OWNER", null, null, null, List.of(), null, null, USER_ID, null);
    }
}
