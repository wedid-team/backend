package com.wedid.backend.sdm.service;

import com.wedid.backend.estimate.entity.CalculationPolicy;
import com.wedid.backend.estimate.entity.SdmBudgetBand;
import com.wedid.backend.estimate.repository.SdmBudgetBandRepository;
import com.wedid.backend.sdm.repository.SdmBasePriceRepository;
import com.wedid.backend.sdm.service.SdmBasePrices.VendorPrice;
import com.wedid.backend.sdm.service.SdmCombinationSelector.Candidate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 계획의 스드메 예상 비용 (GET /plans/me의 estimate.sdm, 스드메 단계 예상 비용 → budget-status 판정).
 * <ul>
 *   <li>COMBINATION_DATA: 고른 예산대 안 조합 합계의 하위 25% ~ 75% 지점 (조합 API와 같은 기본가 · 합계 규칙)</li>
 *   <li>BAND_BOUNDARY: 스드메 가격 데이터가 없을 때만 예산대 경계값으로 대신한다.
 *       하한이 없는 BASIC · 상한이 없는 LUXURY는 한쪽 값을 알 수 없어 미정(null)이다.</li>
 *   <li>경계가 미정(DRAFT)이거나, 데이터는 있는데 예산대 안 조합이 없으면 미정(null)이다. 0으로 보지 않는다.</li>
 * </ul>
 */
@Component
public class SdmEstimateCalculator {

    public enum SourceType {
        COMBINATION_DATA,
        BAND_BOUNDARY
    }

    public enum PendingReason {
        TIER_BOUNDARY_PENDING,
        OPEN_ENDED_TIER,
        NO_COMBINATION_IN_TIER
    }

    /**
     * @param min / max              원 단위. 미정이면 둘 다 null
     * @param sourceType             값을 만든 근거. 경계 미정이면 null
     * @param pendingReason          미정인 이유. 값이 있으면 null
     * @param combinationCountInTier COMBINATION_DATA일 때 예산대 안 조합 수
     */
    public record SdmEstimate(
            BigDecimal min,
            BigDecimal max,
            SourceType sourceType,
            PendingReason pendingReason,
            Long combinationCountInTier
    ) {
        public boolean isKnown() {
            return min != null && max != null;
        }
    }

    static final double LOWER_QUANTILE = 0.25;
    static final double UPPER_QUANTILE = 0.75;

    private final SdmBudgetBandRepository bandRepository;
    private final SdmBasePriceRepository basePriceRepository;

    public SdmEstimateCalculator(SdmBudgetBandRepository bandRepository, SdmBasePriceRepository basePriceRepository) {
        this.bandRepository = bandRepository;
        this.basePriceRepository = basePriceRepository;
    }

    public SdmEstimate estimate(CalculationPolicy policy, SdmBudgetBand band) {
        List<SdmBudgetBand> bands = bandRepository.findByPolicyIdOrderBySortOrder(policy.getId());
        Optional<TierRange> range = TierRange.of(band, bands);
        if (range.isEmpty()) {
            return new SdmEstimate(null, null, null, PendingReason.TIER_BOUNDARY_PENDING, null);
        }

        Map<String, List<VendorPrice>> byCategory = vendorPricesByCategory();
        if (!hasAllCategories(byCategory)) {
            if (band.getLowerAmount() == null || band.getUpperAmount() == null) {
                return new SdmEstimate(null, null, SourceType.BAND_BOUNDARY, PendingReason.OPEN_ENDED_TIER, null);
            }
            return new SdmEstimate(band.getLowerAmount(), band.getUpperAmount(), SourceType.BAND_BOUNDARY, null, null);
        }

        long[] sums = SdmCombinationSelector.sortedTierSums(
                candidates(byCategory, "STUDIO"), candidates(byCategory, "DRESS"), candidates(byCategory, "MAKEUP"),
                range.get());
        if (sums.length == 0) {
            return new SdmEstimate(null, null, SourceType.COMBINATION_DATA, PendingReason.NO_COMBINATION_IN_TIER, 0L);
        }
        return new SdmEstimate(
                BigDecimal.valueOf(SdmCombinationSelector.quantile(sums, LOWER_QUANTILE)),
                BigDecimal.valueOf(SdmCombinationSelector.quantile(sums, UPPER_QUANTILE)),
                SourceType.COMBINATION_DATA, null, (long) sums.length);
    }

    /** 대표 구성 기본가가 있는 업체를 카테고리별로. */
    public Map<String, List<VendorPrice>> vendorPricesByCategory() {
        return SdmBasePrices.aggregate(basePriceRepository.findRepresentativePriceRows()).stream()
                .collect(Collectors.groupingBy(VendorPrice::category));
    }

    public static boolean hasAllCategories(Map<String, List<VendorPrice>> byCategory) {
        return SdmBasePrices.CATEGORIES.stream().noneMatch(c -> byCategory.getOrDefault(c, List.of()).isEmpty());
    }

    public static List<Candidate> candidates(Map<String, List<VendorPrice>> byCategory, String category) {
        return byCategory.getOrDefault(category, List.of()).stream()
                .map(v -> new Candidate(v.vendorId(), v.basePrice(), v.publicAgencyCount() == null ? 0 : v.publicAgencyCount()))
                .toList();
    }
}
