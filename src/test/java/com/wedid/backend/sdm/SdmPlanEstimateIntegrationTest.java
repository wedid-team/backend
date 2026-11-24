package com.wedid.backend.sdm;

import com.wedid.backend.estimate.dto.EstimateRequest.WeddingDateType;
import com.wedid.backend.estimate.dto.HallCostEstimate;
import com.wedid.backend.estimate.entity.EstimateRun.CalculationStatus;
import com.wedid.backend.estimate.service.EstimateService;
import com.wedid.backend.plan.dto.PlanRequest;
import com.wedid.backend.plan.dto.PlanResponse;
import com.wedid.backend.plan.service.PlanEstimateRecorder;
import com.wedid.backend.plan.service.PlanPolicyResolver;
import com.wedid.backend.plan.service.PlanService;
import com.wedid.backend.roadmap.dto.BudgetStatusResponse;
import com.wedid.backend.roadmap.dto.BudgetStatusResponse.SkipReason;
import com.wedid.backend.roadmap.dto.BudgetStatusResponse.Verdict;
import com.wedid.backend.roadmap.service.RoadmapService;
import com.wedid.backend.sdm.repository.SdmBasePriceRepository;
import com.wedid.backend.sdm.service.SdmEstimateCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 확정된 예산대 경계(sql/035 시드 그대로)로 계획의 스드메 예상 비용과 남은 예산 판정을 확인한다.
 * 예식장 예상 비용은 2,000만~3,000만 원 고정, 예산 3,000만 원. 각 테스트는 롤백된다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({PlanService.class, PlanPolicyResolver.class, PlanEstimateRecorder.class, RoadmapService.class,
        SdmEstimateCalculator.class, SdmBasePriceRepository.class, SdmPlanEstimateIntegrationTest.TestConfig.class})
@Sql({"/sql/plan-fixture.sql", "file:sql/035_seed_active_policy_sdm_bands.sql"})
class SdmPlanEstimateIntegrationTest {

    static final UUID GROOM = UUID.fromString("00000000-0000-0000-0000-0000000000a1");

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
    private PlanService planService;

    @Autowired
    private RoadmapService roadmapService;

    @Autowired
    private TestEntityManager entityManager;

    @MockitoBean
    private EstimateService estimateService;

    @BeforeEach
    void setUp() {
        when(estimateService.calculateHallCostEstimate(any())).thenReturn(Optional.of(new HallCostEstimate(
                new BigDecimal("20000000"), new BigDecimal("30000000"), "HALL_DATA", null, null, null, 12)));
    }

    @Test
    void 스드메_데이터가_없으면_예산대_경계값으로_채우고_남은_예산을_판정한다() {
        PlanResponse plan = create("STANDARD");

        assertThat(plan.sdmBand().lowerAmount()).isEqualByComparingTo("3600000");
        assertThat(plan.sdmBand().upperAmount()).isEqualByComparingTo("4200000");
        assertThat(plan.estimate().sdm().min()).isEqualByComparingTo("3600000");
        assertThat(plan.estimate().sdm().max()).isEqualByComparingTo("4200000");
        assertThat(plan.estimate().sdm().sourceType()).isEqualTo("BAND_BOUNDARY");
        assertThat(plan.estimate().total().min()).isEqualByComparingTo("23600000");
        assertThat(plan.estimate().total().max()).isEqualByComparingTo("34200000");
        assertThat(plan.estimate().calculationStatus()).isEqualTo(CalculationStatus.COMPLETE);
        flushAndClear();

        // GET /plans/me도 같은 값
        PlanResponse mine = planService.getMine(GROOM);
        assertThat(mine.estimate().sdm().min()).isEqualByComparingTo("3600000");
        assertThat(mine.estimate().sdm().sourceType()).isEqualTo("BAND_BOUNDARY");

        // 남은 예산 3,000만 vs 남은 예상 비용(예식장 + 스드메) 2,360만~3,420만 → 빠듯
        BudgetStatusResponse status = roadmapService.getBudgetStatus(GROOM);
        assertThat(status.remainingEstimate().min()).isEqualByComparingTo("23600000");
        assertThat(status.remainingEstimate().max()).isEqualByComparingTo("34200000");
        assertThat(status.verdict()).isEqualTo(Verdict.TIGHT);
        assertThat(status.verdictSkipReason()).isNull();
    }

    @Test
    void 상한이_없는_럭셔리를_경계값으로_대신할_때는_미정이라_판정하지_않는다() {
        PlanResponse plan = create("LUXURY");

        assertThat(plan.estimate().sdm().min()).isNull();
        assertThat(plan.estimate().sdm().max()).isNull();
        assertThat(plan.estimate().sdm().sourceType()).isEqualTo("BAND_BOUNDARY");
        assertThat(plan.estimate().sdm().pendingReason()).isEqualTo("OPEN_ENDED_TIER");
        assertThat(plan.estimate().calculationStatus()).isEqualTo(CalculationStatus.PARTIAL);
        flushAndClear();

        assertThat(roadmapService.getBudgetStatus(GROOM).verdictSkipReason()).isEqualTo(SkipReason.ESTIMATE_PENDING);
    }

    @Test
    @Sql({"/sql/plan-fixture.sql", "file:sql/035_seed_active_policy_sdm_bands.sql", "/sql/sdm-fixture.sql"})
    void 스드메_데이터가_있으면_예산대_안_조합_합계의_25_75퍼센트_지점으로_채운다() {
        PlanResponse plan = create("STANDARD");

        // sdm-fixture 조합 125개 중 [360만, 420만) 안 33개의 하위 25% · 75% 지점
        assertThat(plan.estimate().sdm().min()).isEqualByComparingTo("3750000");
        assertThat(plan.estimate().sdm().max()).isEqualByComparingTo("4027500");
        assertThat(plan.estimate().sdm().sourceType()).isEqualTo("COMBINATION_DATA");
        assertThat(plan.steps().stream().filter(s -> s.code().equals("SDM_CONTRACT")).findFirst().orElseThrow()
                .estimated().min()).isEqualByComparingTo("3750000");
        flushAndClear();

        BudgetStatusResponse status = roadmapService.getBudgetStatus(GROOM);
        assertThat(status.remainingEstimate().min()).isEqualByComparingTo("23750000");
        assertThat(status.remainingEstimate().max()).isEqualByComparingTo("34027500");
        assertThat(status.verdict()).isEqualTo(Verdict.TIGHT);
    }

    @Test
    @Sql({"/sql/plan-fixture.sql", "file:sql/035_seed_active_policy_sdm_bands.sql", "/sql/sdm-fixture.sql"})
    void 예산대를_바꾸면_스드메_예상_비용과_판정이_새_예산대로_바뀐다() {
        create("STANDARD");
        flushAndClear();

        PlanResponse premium = planService.overwriteMine(GROOM, request("PREMIUM", 40_000_000L));
        assertThat(premium.estimate().sdm().min()).isEqualByComparingTo("4380000");
        assertThat(premium.estimate().sdm().max()).isEqualByComparingTo("4867500");
        flushAndClear();

        // 남은 예산 4,000만 vs 2,438만~3,486.75만 → 여유
        BudgetStatusResponse status = roadmapService.getBudgetStatus(GROOM);
        assertThat(status.remainingEstimate().max()).isEqualByComparingTo("34867500");
        assertThat(status.verdict()).isEqualTo(Verdict.ENOUGH);
    }

    private PlanResponse create(String bandCode) {
        return planService.create(GROOM, request(bandCode, 30_000_000L));
    }

    private static PlanRequest request(String bandCode, Long budget) {
        return new PlanRequest("PLAN_TEST_REGION", "FROM_150_TO_200", WeddingDateType.EXACT,
                LocalDate.of(2027, 10, 9), null, null, budget, List.of("BRIGHT"), bandCode);
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}
