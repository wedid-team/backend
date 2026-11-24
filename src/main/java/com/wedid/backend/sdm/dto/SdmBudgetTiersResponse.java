package com.wedid.backend.sdm.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * 스드메 예산대 4구간 (E-09 · Q-05). 경계값은 정책 데이터(sdm_budget_bands)에서 읽는다.
 *
 * @param policyVersion   계산 정책 버전
 * @param policyStatus    ACTIVE 또는 DRAFT. ACTIVE 정책이 없으면 DRAFT를 쓴다
 * @param defaultTierCode 기본 예산대 (E-10 중간 구간)
 * @param tiers           구간 순서대로
 */
public record SdmBudgetTiersResponse(
        String policyVersion,
        String policyStatus,
        String defaultTierCode,
        List<Tier> tiers
) {

    /**
     * @param lowerAmount     하한(원). 첫 구간에서 null이면 하한 없음, 그 밖에서는 미정
     * @param upperAmount     상한(원). 마지막 구간에서 null이면 상한 없음, 그 밖에서는 미정
     * @param boundaryDefined 경계가 정해져 조합을 고를 수 있는지. false면 조합 API가 TIER_BOUNDARY_PENDING을 준다
     */
    public record Tier(
            String code,
            String label,
            int sortOrder,
            BigDecimal lowerAmount,
            boolean lowerInclusive,
            BigDecimal upperAmount,
            boolean upperInclusive,
            boolean boundaryDefined
    ) {
    }
}
