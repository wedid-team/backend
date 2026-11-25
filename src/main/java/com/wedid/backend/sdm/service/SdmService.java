package com.wedid.backend.sdm.service;

import com.wedid.backend.estimate.entity.CalculationPolicy;
import com.wedid.backend.estimate.entity.SdmBudgetBand;
import com.wedid.backend.estimate.repository.SdmBudgetBandRepository;
import com.wedid.backend.plan.entity.Plan;
import com.wedid.backend.plan.repository.PlanMemberRepository;
import com.wedid.backend.plan.repository.PlanRepository;
import com.wedid.backend.plan.service.PlanPolicyResolver;
import com.wedid.backend.sdm.dto.SdmBudgetTiersResponse;
import com.wedid.backend.sdm.dto.SdmCombinationsResponse;
import com.wedid.backend.sdm.dto.SdmCombinationsResponse.UnavailableReason;
import com.wedid.backend.sdm.service.SdmBasePrices.VendorPrice;
import com.wedid.backend.sdm.service.SdmCombinationSelector.Pick;
import com.wedid.backend.sdm.service.SdmCombinationSelector.Selection;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 스드메 예산대 · 조합 (E-09 · E-20 · D-20). 웨딩홀 선택과 상관없이 동작한다.
 * 조합은 저장하지 않고 조회할 때마다 만든다. 예산대를 바꾸면 그 예산대로 새 조합을 돌려준다.
 */
@Service
public class SdmService {

    static final String PRICE_RULE = "업체 기본가 = 대표 구성(스튜디오 촬영, 드레스·메이크업 본식+촬영) 공개 가격 행마다 (최저+최고)/2를 구한 뒤 "
            + "그 값들의 중앙값(원 단위 반올림). 조합 합계 = 스튜디오 · 드레스 · 메이크업 기본가의 합. "
            + "단품가 범위는 같은 가격 행의 최저 ~ 최고";
    static final String SELECTION_RULE = "고른 예산대 안 조합만 대상으로, 합계를 낮은 순으로 놓고 하위 25% · 50% · 75% 지점 금액에 "
            + "가장 가까운 조합을 실속형 · 균형형 · 프리미엄형 순서로 고름. 앞에서 고른 조합과 업체가 겹치면 제외하고, "
            + "조건에 맞는 조합이 없으면 그 유형은 비움(있는 만큼만 제공). 차이가 같으면 공개 대행사 수 합이 많은 조합, "
            + "그다음 업체 ID 순. 다른 예산대 조합으로 대신 채우지 않음";
    static final String PRICE_BASIS = "참가격 결혼준비대행 중요정보공개(서울)에 공개된 대행사별 판매가";
    static final String EXTRA_COST_RULE = "원본 사진 · 드레스 업그레이드 · 헬퍼비 등 추가 옵션은 합계에 포함하지 않음";

    private final PlanPolicyResolver policyResolver;
    private final SdmBudgetBandRepository bandRepository;
    private final PlanMemberRepository planMemberRepository;
    private final PlanRepository planRepository;
    private final SdmEstimateCalculator estimateCalculator;

    public SdmService(
            PlanPolicyResolver policyResolver,
            SdmBudgetBandRepository bandRepository,
            PlanMemberRepository planMemberRepository,
            PlanRepository planRepository,
            SdmEstimateCalculator estimateCalculator
    ) {
        this.policyResolver = policyResolver;
        this.bandRepository = bandRepository;
        this.planMemberRepository = planMemberRepository;
        this.planRepository = planRepository;
        this.estimateCalculator = estimateCalculator;
    }

    @Transactional(readOnly = true)
    public SdmBudgetTiersResponse getBudgetTiers() {
        CalculationPolicy policy = policyResolver.currentPolicy();
        List<SdmBudgetBand> bands = bandRepository.findByPolicyIdOrderBySortOrder(policy.getId());
        List<SdmBudgetTiersResponse.Tier> tiers = bands.stream()
                .map(band -> new SdmBudgetTiersResponse.Tier(
                        band.getCode(), band.getLabel(), band.getSortOrder(),
                        band.getLowerAmount(), band.isLowerInclusive(),
                        band.getUpperAmount(), band.isUpperInclusive(),
                        TierRange.of(band, bands).isPresent()))
                .toList();
        return new SdmBudgetTiersResponse(policy.getVersion(), policy.getStatus(), policyResolver.defaultBandCode(policy), tiers);
    }

    /**
     * @param tierCode 예산대 코드. 없으면 내 계획의 예산대, 계획이 없으면 정책 기본값
     */
    @Transactional(readOnly = true)
    public SdmCombinationsResponse getCombinations(UUID userId, String tierCode) {
        CalculationPolicy policy = policyResolver.currentPolicy();
        List<SdmBudgetBand> bands = bandRepository.findByPolicyIdOrderBySortOrder(policy.getId());
        SdmBudgetBand band = chooseBand(policy, bands, userId, tierCode);
        SdmCombinationsResponse.Tier tier = new SdmCombinationsResponse.Tier(
                band.getCode(), band.getLabel(),
                band.getLowerAmount(), band.isLowerInclusive(), band.getUpperAmount(), band.isUpperInclusive());

        Optional<TierRange> range = TierRange.of(band, bands);
        if (range.isEmpty()) {
            return new SdmCombinationsResponse(tier, List.of(), UnavailableReason.TIER_BOUNDARY_PENDING,
                    basis(policy, Map.of(), 0, 0, List.of()));
        }

        Map<String, List<VendorPrice>> byCategory = estimateCalculator.vendorPricesByCategory();
        if (!SdmEstimateCalculator.hasAllCategories(byCategory)) {
            return new SdmCombinationsResponse(tier, List.of(), UnavailableReason.NO_PRICE_DATA,
                    basis(policy, byCategory, 0, 0, List.of()));
        }

        Selection selection = SdmCombinationSelector.select(
                SdmEstimateCalculator.candidates(byCategory, "STUDIO"),
                SdmEstimateCalculator.candidates(byCategory, "DRESS"),
                SdmEstimateCalculator.candidates(byCategory, "MAKEUP"),
                range.get());
        Map<Long, VendorPrice> vendorById = byCategory.values().stream()
                .flatMap(List::stream)
                .collect(Collectors.toMap(VendorPrice::vendorId, Function.identity()));

        List<SdmCombinationsResponse.Combination> combinations = selection.picks().stream()
                .map(pick -> toCombination(pick, vendorById))
                .toList();
        List<SdmCombinationsResponse.Target> targets = selection.targets().entrySet().stream()
                .map(entry -> new SdmCombinationsResponse.Target(
                        entry.getKey().name(), entry.getKey().quantile(), BigDecimal.valueOf(entry.getValue())))
                .toList();

        return new SdmCombinationsResponse(
                tier,
                combinations,
                combinations.isEmpty() ? UnavailableReason.NO_COMBINATION_IN_TIER : null,
                basis(policy, byCategory, selection.combinationCount(), selection.combinationCountInTier(), targets));
    }

    private SdmBudgetBand chooseBand(CalculationPolicy policy, List<SdmBudgetBand> bands, UUID userId, String tierCode) {
        if (tierCode != null) {
            return bands.stream()
                    .filter(band -> band.getCode().equals(tierCode))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("스드메 예산대를 찾을 수 없어요: " + tierCode));
        }
        String planBandCode = planMemberRepository.findByUserId(userId)
                .flatMap(member -> planRepository.findByIdAndStatus(member.getPlanId(), Plan.Status.ACTIVE))
                .flatMap(plan -> bandRepository.findById(plan.getSelectedBandId()))
                .map(SdmBudgetBand::getCode)
                .orElse(null);
        // 계획의 예산대가 이전 정책 버전 것이어도 같은 코드의 현재 정책 예산대를 쓴다
        return policyResolver.band(policy, planBandCode, null);
    }

    private static SdmCombinationsResponse.Combination toCombination(Pick pick, Map<Long, VendorPrice> vendorById) {
        List<SdmCombinationsResponse.Item> items = List.of(pick.studio(), pick.dress(), pick.makeup()).stream()
                .map(candidate -> toItem(vendorById.get(candidate.vendorId())))
                .toList();
        return new SdmCombinationsResponse.Combination(
                pick.type().name(), pick.type().label(),
                BigDecimal.valueOf(pick.total()), BigDecimal.valueOf(pick.target()), items);
    }

    private static SdmCombinationsResponse.Item toItem(VendorPrice vendor) {
        return new SdmCombinationsResponse.Item(
                vendor.category(),
                vendor.vendorId(),
                vendor.vendorName(),
                SdmBasePrices.REPRESENTATIVE_PRODUCT_TYPES.get(vendor.category()),
                BigDecimal.valueOf(vendor.basePrice()),
                new SdmCombinationsResponse.PriceRange(BigDecimal.valueOf(vendor.rangeMin()), BigDecimal.valueOf(vendor.rangeMax())),
                vendor.priceRowCount(),
                vendor.publicAgencyCount(),
                vendor.officialUrl(),
                vendor.sources().stream()
                        .map(s -> new SdmCombinationsResponse.Source(s.agencyName(), s.sourceUrl(), s.fileReference(), s.collectedAt()))
                        .toList());
    }

    private static SdmCombinationsResponse.Basis basis(
            CalculationPolicy policy,
            Map<String, List<VendorPrice>> byCategory,
            long combinationCount,
            long combinationCountInTier,
            List<SdmCombinationsResponse.Target> targets
    ) {
        return new SdmCombinationsResponse.Basis(
                PRICE_RULE,
                SELECTION_RULE,
                PRICE_BASIS,
                EXTRA_COST_RULE,
                SdmBasePrices.CATEGORIES.stream()
                        .map(c -> new SdmCombinationsResponse.CategoryValue(c, SdmBasePrices.REPRESENTATIVE_PRODUCT_TYPES.get(c)))
                        .toList(),
                SdmBasePrices.CATEGORIES.stream()
                        .map(c -> new SdmCombinationsResponse.CategoryCount(c, byCategory.getOrDefault(c, List.of()).size()))
                        .toList(),
                combinationCount,
                combinationCountInTier,
                targets,
                policy.getVersion());
    }
}
