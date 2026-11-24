package com.wedid.backend.sdm;

import com.wedid.backend.estimate.dto.EstimateRequest.WeddingDateType;
import com.wedid.backend.estimate.dto.HallCostEstimate;
import com.wedid.backend.estimate.service.EstimateService;
import com.wedid.backend.plan.dto.PlanRequest;
import com.wedid.backend.plan.service.PlanEstimateRecorder;
import com.wedid.backend.plan.service.PlanPolicyResolver;
import com.wedid.backend.plan.service.PlanService;
import com.wedid.backend.sdm.dto.SdmBudgetTiersResponse;
import com.wedid.backend.sdm.dto.SdmCombinationsResponse;
import com.wedid.backend.sdm.dto.SdmCombinationsResponse.Combination;
import com.wedid.backend.sdm.dto.SdmCombinationsResponse.Item;
import com.wedid.backend.sdm.dto.SdmCombinationsResponse.UnavailableReason;
import com.wedid.backend.sdm.repository.SdmBasePriceRepository;
import com.wedid.backend.sdm.service.SdmEstimateCalculator;
import com.wedid.backend.sdm.service.SdmService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 로컬 PostgreSQL에 대해 스드메 예산대 · 조합 조회를 검증한다.
 * 업체 · 가격은 실제 XLSX에서 옮긴 sdm-fixture.sql, 경계는 테스트 전용 250 · 350 · 450만 원(plan-active-policy-fixture.sql).
 * 기대값은 같은 규칙을 Python으로 따로 계산해 맞췄다. 각 테스트는 롤백된다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({SdmService.class, SdmEstimateCalculator.class, SdmBasePriceRepository.class, PlanPolicyResolver.class,
        PlanService.class, PlanEstimateRecorder.class, SdmServiceIntegrationTest.TestConfig.class})
@Sql({"/sql/plan-fixture.sql", "/sql/plan-active-policy-fixture.sql", "/sql/sdm-fixture.sql"})
class SdmServiceIntegrationTest {

    static final UUID GROOM = UUID.fromString("00000000-0000-0000-0000-0000000000a1");

    // sdm-fixture.sql 업체 ID
    static final long LE_AN = 990001;          // 스튜디오 르안
    static final long LAURA = 990002;          // 스튜디오 로라스튜디오 (토탈 구성 행 1개는 빠져야 한다)
    static final long ILD_HANNAM = 990004;     // 스튜디오 일드한남
    static final long POLARIS = 990006;        // 드레스 폴라리스
    static final long LIAN_MARIE = 990007;     // 드레스 리안마리
    static final long CLARA = 990009;          // 드레스 클라라
    static final long LONA = 990011;           // 메이크업 로나
    static final long LULU = 990012;           // 메이크업 룰루
    static final long VOID = 990013;           // 메이크업 보이드바이박철
    static final long ADIDO = 990015;          // 메이크업 아디도

    @TestConfiguration
    static class TestConfig {
        @Bean
        Clock clock() {
            return Clock.fixed(Instant.parse("2026-11-24T03:00:00Z"), ZoneOffset.UTC);
        }

        @Bean
        JsonMapper jsonMapper() {
            return JsonMapper.builder().build();
        }
    }

    @Autowired
    private SdmService sdmService;

    @Autowired
    private PlanService planService;

    @MockitoBean
    private EstimateService estimateService;

    @BeforeEach
    void setUp() {
        when(estimateService.calculateHallCostEstimate(any())).thenReturn(Optional.of(new HallCostEstimate(
                new BigDecimal("20000000"), new BigDecimal("30000000"), "HALL_DATA", null, null, null, 12)));
    }

    @Test
    void 예산대_안_조합에서_하위_25_50_75퍼센트_지점에_가까운_조합을_고른다() {
        SdmCombinationsResponse response = sdmService.getCombinations(GROOM, "PREMIUM");

        assertThat(response.tier().code()).isEqualTo("PREMIUM");
        assertThat(response.unavailableReason()).isNull();
        assertThat(response.combinations()).extracting(Combination::type)
                .containsExactly("VALUE", "BALANCED", "PREMIUM");
        assertThat(response.combinations()).extracting(Combination::typeLabel)
                .containsExactly("실속형", "균형형", "프리미엄형");
        assertThat(response.combinations()).extracting(c -> c.totalBasePrice().longValueExact())
                .containsExactly(3_717_500L, 3_900_000L, 4_275_000L);
        assertThat(response.combinations()).extracting(c -> c.targetAmount().longValueExact())
                .containsExactly(3_717_500L, 3_937_500L, 4_215_000L);
        assertThat(vendorIds(response.combinations().get(0))).containsExactly(ILD_HANNAM, POLARIS, LULU);
        assertThat(vendorIds(response.combinations().get(1))).containsExactly(LAURA, LIAN_MARIE, ADIDO);
        assertThat(vendorIds(response.combinations().get(2))).containsExactly(LE_AN, CLARA, VOID);

        SdmCombinationsResponse.Basis basis = response.basis();
        assertThat(basis.candidateVendorCounts()).extracting(SdmCombinationsResponse.CategoryCount::count)
                .containsExactly(5, 5, 5);
        assertThat(basis.combinationCount()).isEqualTo(125);
        assertThat(basis.combinationCountInTier()).isEqualTo(54);
        assertThat(basis.targets()).extracting(SdmCombinationsResponse.Target::type)
                .containsExactly("VALUE", "BALANCED", "PREMIUM");
        assertThat(basis.selectionRule()).contains("25%", "겹치면 제외", "있는 만큼");
        assertThat(basis.priceRule()).contains("중앙값");
        assertThat(basis.extraCostRule()).contains("포함하지 않음");
        assertThat(basis.policyVersion()).isEqualTo("plan-test-active");
    }

    @Test
    void 조합_항목은_업체_기본가_단품가_범위_공개_대행사_수_출처를_준다() {
        Item studio = sdmService.getCombinations(GROOM, "PREMIUM").combinations().get(0).items().get(0);

        assertThat(studio.category()).isEqualTo("STUDIO");
        assertThat(studio.vendorId()).isEqualTo(ILD_HANNAM);
        assertThat(studio.vendorName()).isEqualTo("일드한남");
        assertThat(studio.productType()).isEqualTo("촬영");
        assertThat(studio.basePrice()).isEqualByComparingTo("1842500");
        assertThat(studio.priceRange().min()).isEqualByComparingTo("1650000");
        assertThat(studio.priceRange().max()).isEqualByComparingTo("1980000");
        assertThat(studio.priceRowCount()).isEqualTo(6);
        assertThat(studio.publicAgencyCount()).isEqualTo(5);
        assertThat(studio.officialUrl()).isNull();
        assertThat(studio.sources()).isNotEmpty().allSatisfy(source -> {
            assertThat(source.agencyName()).isNotBlank();
            assertThat(source.sourceUrl()).contains("price.go.kr");
        });
    }

    @Test
    void 대표_구성이_아닌_가격_행은_기본가에_넣지_않는다() {
        Item laura = sdmService.getCombinations(GROOM, "PREMIUM").combinations().get(1).items().get(0);

        // 로라스튜디오는 촬영 3행 + 토탈 1행이 있다
        assertThat(laura.vendorId()).isEqualTo(LAURA);
        assertThat(laura.priceRowCount()).isEqualTo(3);
        assertThat(laura.basePrice()).isEqualByComparingTo("1390000");
    }

    @Test
    void 업체가_겹치지_않는_조합이_모자라면_있는_만큼만_준다() {
        SdmCombinationsResponse response = sdmService.getCombinations(GROOM, "STANDARD");

        assertThat(response.basis().combinationCountInTier()).isEqualTo(16);
        assertThat(response.combinations()).extracting(Combination::type).containsExactly("VALUE", "BALANCED");
        assertThat(vendorIds(response.combinations().get(0))).containsExactly(LE_AN, POLARIS, VOID);
        assertThat(vendorIds(response.combinations().get(1))).containsExactly(LAURA, LIAN_MARIE, LONA);
        assertThat(response.unavailableReason()).isNull();
    }

    @Test
    void 예산대를_바꾸면_그_예산대_안의_새_조합을_준다() {
        SdmCombinationsResponse luxury = sdmService.getCombinations(GROOM, "LUXURY");

        assertThat(luxury.tier().code()).isEqualTo("LUXURY");
        assertThat(luxury.tier().upperAmount()).isNull();
        assertThat(luxury.combinations()).extracting(c -> c.totalBasePrice().longValueExact())
                .containsExactly(4_867_500L, 5_545_000L, 5_450_000L)
                .allSatisfy(total -> assertThat(total).isGreaterThan(4_500_000L));
    }

    @Test
    void 예산대_안에_조합이_없으면_빈_목록과_이유를_준다() {
        SdmCombinationsResponse response = sdmService.getCombinations(GROOM, "BASIC");

        assertThat(response.combinations()).isEmpty();
        assertThat(response.unavailableReason()).isEqualTo(UnavailableReason.NO_COMBINATION_IN_TIER);
        assertThat(response.basis().combinationCount()).isEqualTo(125);
        assertThat(response.basis().combinationCountInTier()).isZero();
        assertThat(response.basis().targets()).isEmpty();
    }

    @Test
    void 예산대를_보내지_않으면_계획의_예산대_계획이_없으면_정책_기본값을_쓴다() {
        assertThat(sdmService.getCombinations(GROOM, null).tier().code()).isEqualTo("STANDARD");

        planService.create(GROOM, new PlanRequest("PLAN_TEST_REGION", "FROM_150_TO_200", WeddingDateType.EXACT,
                LocalDate.of(2027, 10, 9), null, null, 30_000_000L, List.of("BRIGHT"), "LUXURY"));

        assertThat(sdmService.getCombinations(GROOM, null).tier().code()).isEqualTo("LUXURY");
    }

    @Test
    void 없는_예산대는_400이다() {
        assertThatThrownBy(() -> sdmService.getCombinations(GROOM, "GOLD"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("GOLD");
    }

    @Test
    @Sql({"/sql/plan-fixture.sql", "/sql/draft-policy-only.sql", "/sql/sdm-fixture.sql"})
    void 예산대_경계가_미정이면_조합을_고르지_않는다() {
        SdmCombinationsResponse response = sdmService.getCombinations(GROOM, "STANDARD");

        assertThat(response.combinations()).isEmpty();
        assertThat(response.unavailableReason()).isEqualTo(UnavailableReason.TIER_BOUNDARY_PENDING);
        assertThat(response.tier().lowerAmount()).isNull();
    }

    @Test
    @Sql({"/sql/plan-fixture.sql", "/sql/plan-active-policy-fixture.sql"})
    void 스드메_가격_데이터가_없으면_조합을_고르지_않는다() {
        SdmCombinationsResponse response = sdmService.getCombinations(GROOM, "STANDARD");

        assertThat(response.combinations()).isEmpty();
        assertThat(response.unavailableReason()).isEqualTo(UnavailableReason.NO_PRICE_DATA);
    }

    @Test
    @Sql({"/sql/plan-fixture.sql", "file:sql/035_seed_active_policy_sdm_bands.sql"})
    void 확정된_예산대_경계를_ACTIVE_정책에서_읽는다() {
        SdmBudgetTiersResponse response = sdmService.getBudgetTiers();

        assertThat(response.policyVersion()).isEqualTo("2026-11-active-1");
        assertThat(response.policyStatus()).isEqualTo("ACTIVE");
        assertThat(response.defaultTierCode()).isEqualTo("STANDARD");
        assertThat(response.tiers()).extracting(SdmBudgetTiersResponse.Tier::code)
                .containsExactly("BASIC", "STANDARD", "PREMIUM", "LUXURY");
        assertThat(response.tiers()).extracting(SdmBudgetTiersResponse.Tier::lowerAmount)
                .usingComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .containsExactly(null, new BigDecimal("3600000"), new BigDecimal("4200000"), new BigDecimal("5300000"));
        assertThat(response.tiers()).extracting(SdmBudgetTiersResponse.Tier::upperAmount)
                .usingComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .containsExactly(new BigDecimal("3600000"), new BigDecimal("4200000"), new BigDecimal("5300000"), null);
        assertThat(response.tiers()).allSatisfy(tier -> {
            assertThat(tier.lowerInclusive()).isTrue();
            assertThat(tier.upperInclusive()).isFalse();
            assertThat(tier.boundaryDefined()).isTrue();
        });
    }

    @Test
    @Sql({"/sql/plan-fixture.sql", "/sql/draft-policy-only.sql"})
    void DRAFT_정책의_예산대는_경계_미정으로_준다() {
        SdmBudgetTiersResponse response = sdmService.getBudgetTiers();

        assertThat(response.policyStatus()).isEqualTo("DRAFT");
        assertThat(response.tiers()).hasSize(4).allSatisfy(tier -> {
            assertThat(tier.lowerAmount()).isNull();
            assertThat(tier.upperAmount()).isNull();
            assertThat(tier.boundaryDefined()).isFalse();
        });
    }

    private static List<Long> vendorIds(Combination combination) {
        return combination.items().stream().map(Item::vendorId).toList();
    }
}
