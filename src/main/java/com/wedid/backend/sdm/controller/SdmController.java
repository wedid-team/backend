package com.wedid.backend.sdm.controller;

import com.wedid.backend.auth.web.LoginUser;
import com.wedid.backend.sdm.dto.SdmBudgetTiersResponse;
import com.wedid.backend.sdm.dto.SdmCombinationsResponse;
import com.wedid.backend.sdm.service.SdmService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 스드메 예산대 · 조합.
 * <ul>
 *   <li>GET /api/v1/sdm/budget-tiers: 예산대 4구간. 비회원도 조회 가능 (C-01 예산대 변경)</li>
 *   <li>GET /api/v1/sdm/combinations?tier=: 고른 예산대 안 조합 최대 3개. 로그인 필요 (C-01 스드메 후보)</li>
 * </ul>
 * 계획의 예산대를 바꾸는 것은 PUT /api/v1/plans/me (D-20: 예산대를 바꾸면 조합과 계획의 예산대가 함께 바뀜).
 */
@RestController
@RequestMapping("/api/v1/sdm")
public class SdmController {

    private final SdmService sdmService;

    public SdmController(SdmService sdmService) {
        this.sdmService = sdmService;
    }

    @GetMapping("/budget-tiers")
    public SdmBudgetTiersResponse getBudgetTiers() {
        return sdmService.getBudgetTiers();
    }

    @GetMapping("/combinations")
    public SdmCombinationsResponse getCombinations(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestParam(required = false) String tier
    ) {
        return sdmService.getCombinations(loginUser.id(), tier);
    }
}
