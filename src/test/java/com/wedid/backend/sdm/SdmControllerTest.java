package com.wedid.backend.sdm;

import com.wedid.backend.auth.oauth.OAuthLoginFailureHandler;
import com.wedid.backend.auth.oauth.OAuthLoginSuccessHandler;
import com.wedid.backend.auth.service.OAuthLoginService;
import com.wedid.backend.auth.service.RefreshTokenService;
import com.wedid.backend.auth.token.AccessTokenProvider;
import com.wedid.backend.auth.web.AuthCookieManager;
import com.wedid.backend.global.config.ClockConfig;
import com.wedid.backend.global.config.CorsConfig;
import com.wedid.backend.global.security.SecurityConfig;
import com.wedid.backend.sdm.controller.SdmController;
import com.wedid.backend.sdm.dto.SdmBudgetTiersResponse;
import com.wedid.backend.sdm.dto.SdmCombinationsResponse;
import com.wedid.backend.sdm.service.SdmService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SdmController.class)
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
class SdmControllerTest {

    private static final UUID USER_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AccessTokenProvider accessTokenProvider;

    @MockitoBean
    private SdmService sdmService;

    @MockitoBean
    private RefreshTokenService refreshTokenService;

    @MockitoBean
    private OAuthLoginService oauthLoginService;

    @Test
    void 예산대_목록은_비회원도_볼_수_있다() throws Exception {
        when(sdmService.getBudgetTiers()).thenReturn(new SdmBudgetTiersResponse("2026-11-active-1", "ACTIVE", "STANDARD",
                List.of(new SdmBudgetTiersResponse.Tier("STANDARD", "스탠다드", 2,
                        new BigDecimal("3600000"), true, new BigDecimal("4200000"), false, true))));

        mockMvc.perform(get("/api/v1/sdm/budget-tiers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.defaultTierCode").value("STANDARD"))
                .andExpect(jsonPath("$.tiers[0].lowerAmount").value(3600000))
                .andExpect(jsonPath("$.tiers[0].boundaryDefined").value(true));
    }

    @Test
    void 스드메_조합은_로그인해야_볼_수_있다() throws Exception {
        mockMvc.perform(get("/api/v1/sdm/combinations").param("tier", "STANDARD"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 고른_예산대로_조합을_조회한다() throws Exception {
        when(sdmService.getCombinations(eq(USER_ID), eq("PREMIUM"))).thenReturn(response());

        mockMvc.perform(get("/api/v1/sdm/combinations").param("tier", "PREMIUM").cookie(loggedIn()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tier.code").value("PREMIUM"))
                .andExpect(jsonPath("$.combinations[0].type").value("VALUE"))
                .andExpect(jsonPath("$.combinations[0].items[0].officialUrl").doesNotExist())
                .andExpect(jsonPath("$.combinations[0].items[0].sources[0].agencyName").value("더리본"))
                .andExpect(jsonPath("$.unavailableReason").doesNotExist())
                .andExpect(jsonPath("$.basis.combinationCountInTier").value(54));
    }

    @Test
    void 예산대를_보내지_않으면_서비스가_계획_예산대로_정한다() throws Exception {
        when(sdmService.getCombinations(eq(USER_ID), isNull())).thenReturn(response());

        mockMvc.perform(get("/api/v1/sdm/combinations").cookie(loggedIn()))
                .andExpect(status().isOk());
        verify(sdmService).getCombinations(USER_ID, null);
    }

    @Test
    void 없는_예산대는_400이다() throws Exception {
        when(sdmService.getCombinations(any(), eq("GOLD")))
                .thenThrow(new IllegalArgumentException("스드메 예산대를 찾을 수 없어요: GOLD"));

        mockMvc.perform(get("/api/v1/sdm/combinations").param("tier", "GOLD").cookie(loggedIn()))
                .andExpect(status().isBadRequest());
    }

    private static SdmCombinationsResponse response() {
        SdmCombinationsResponse.Item item = new SdmCombinationsResponse.Item("STUDIO", 1L, "업체", "촬영",
                new BigDecimal("1842500"), new SdmCombinationsResponse.PriceRange(new BigDecimal("1650000"), new BigDecimal("1980000")),
                6, 5, null, List.of(new SdmCombinationsResponse.Source("더리본", "https://www.price.go.kr/", "2026-01", null)));
        return new SdmCombinationsResponse(
                new SdmCombinationsResponse.Tier("PREMIUM", "프리미엄", new BigDecimal("4200000"), true, new BigDecimal("5300000"), false),
                List.of(new SdmCombinationsResponse.Combination("VALUE", "실속형", new BigDecimal("4380000"), new BigDecimal("4380000"), List.of(item))),
                null,
                new SdmCombinationsResponse.Basis("p", "s", "b", "e", List.of(), List.of(), 125, 54, List.of(), "2026-11-active-1"));
    }

    private Cookie loggedIn() {
        return new Cookie(AuthCookieManager.ACCESS_TOKEN_COOKIE, accessTokenProvider.issue(USER_ID));
    }
}
