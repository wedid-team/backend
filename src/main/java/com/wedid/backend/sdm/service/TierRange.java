package com.wedid.backend.sdm.service;

import com.wedid.backend.estimate.entity.SdmBudgetBand;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * 스드메 예산대 하나의 금액 범위. 경계값은 코드에 두지 않고 sdm_budget_bands에서 읽는다(Q-05 미정).
 * <ul>
 *   <li>첫 구간의 하한이 NULL이면 하한 없음, 마지막 구간의 상한이 NULL이면 상한 없음으로 본다.</li>
 *   <li>그 밖의 NULL은 경계 미정이라 범위를 만들 수 없다 (DRAFT 정책의 시드 상태).</li>
 * </ul>
 */
public record TierRange(Long lower, boolean lowerInclusive, Long upper, boolean upperInclusive) {

    /** 경계가 정해지지 않았으면 비어 있다. */
    public static Optional<TierRange> of(SdmBudgetBand band, List<SdmBudgetBand> policyBands) {
        short first = policyBands.stream().map(SdmBudgetBand::getSortOrder).min(Short::compare).orElse(band.getSortOrder());
        short last = policyBands.stream().map(SdmBudgetBand::getSortOrder).max(Short::compare).orElse(band.getSortOrder());

        boolean lowerOpen = band.getLowerAmount() == null;
        boolean upperOpen = band.getUpperAmount() == null;
        if ((lowerOpen && band.getSortOrder() != first) || (upperOpen && band.getSortOrder() != last)) {
            return Optional.empty();
        }
        return Optional.of(new TierRange(
                won(band.getLowerAmount()), band.isLowerInclusive(),
                won(band.getUpperAmount()), band.isUpperInclusive()));
    }

    public boolean contains(long amount) {
        if (lower != null && (lowerInclusive ? amount < lower : amount <= lower)) {
            return false;
        }
        return upper == null || (upperInclusive ? amount <= upper : amount < upper);
    }

    private static Long won(BigDecimal amount) {
        return amount == null ? null : amount.longValueExact();
    }
}
