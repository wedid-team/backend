package com.wedid.backend.plan;

import com.wedid.backend.estimate.dto.EstimateRequest;
import com.wedid.backend.estimate.dto.EstimateRequest.WeddingDateType;
import com.wedid.backend.estimate.dto.HallCostEstimate;
import com.wedid.backend.estimate.entity.EstimateRun;
import com.wedid.backend.estimate.entity.EstimateRun.CalculationStatus;
import com.wedid.backend.estimate.repository.EstimateRunRepository;
import com.wedid.backend.estimate.service.EstimateService;
import com.wedid.backend.plan.dto.PlanRequest;
import com.wedid.backend.plan.dto.PlanResponse;
import com.wedid.backend.plan.service.PlanAlreadyExistsException;
import com.wedid.backend.plan.service.PlanEstimateRecorder;
import com.wedid.backend.plan.service.PlanNotFoundException;
import com.wedid.backend.plan.service.PlanPolicyResolver;
import com.wedid.backend.plan.service.PlanService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
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
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 로컬 PostgreSQL(docker compose)에 대해 계획 저장 · 조회 · 덮어쓰기를 검증한다.
 * sql/034 시드(DRAFT 정책 · 금액 없는 예산대 · 로드맵 6단계)가 적용돼 있어야 한다.
 * 예식장 금액 계산은 이 테스트의 관심사가 아니므로 EstimateService는 고정값을 돌려주도록 대체한다.
 * 각 테스트는 끝나면 롤백된다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({PlanService.class, PlanPolicyResolver.class, PlanEstimateRecorder.class,
        PlanServiceIntegrationTest.TestConfig.class})
@Sql("/sql/plan-fixture.sql")
class PlanServiceIntegrationTest {

    static final UUID GROOM = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    static final UUID BRIDE = UUID.fromString("00000000-0000-0000-0000-0000000000a2");
    static final UUID STRANGER = UUID.fromString("00000000-0000-0000-0000-0000000000a3");

    /** 2026-11-24 (Asia/Seoul) 고정 */
    static final Instant NOW = Instant.parse("2026-11-24T03:00:00Z");

    @TestConfiguration
    static class TestConfig {
        @Bean
        Clock clock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }

        @Bean
        JsonMapper jsonMapper() {
            return JsonMapper.builder().build();
        }
    }

    @Autowired
    private PlanService planService;

    @Autowired
    private EstimateRunRepository estimateRunRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbc;

    @MockitoBean
    private EstimateService estimateService;

    @BeforeEach
    void setUp() {
        hallEstimateReturns("20000000", "30000000");
    }

    @Test
    void DRAFT_정책만_있어도_계획을_만들고_예식장만_계산한_결과를_저장한다() {
        PlanResponse created = planService.create(GROOM, request(WeddingDateType.EXACT, LocalDate.of(2027, 10, 9), null));
        flushAndClear();

        PlanResponse plan = planService.getMine(GROOM);

        assertThat(plan.planId()).isEqualTo(created.planId());
        assertThat(plan.role()).isEqualTo("OWNER");
        assertThat(plan.conditions().regionCode()).isEqualTo("PLAN_TEST_REGION");
        assertThat(plan.conditions().guestRangeCode()).isEqualTo("FROM_150_TO_200");
        assertThat(plan.conditions().budget()).isEqualByComparingTo("30000000");
        assertThat(plan.conditions().moods()).containsExactly("BRIGHT", "CLASSIC");
        assertThat(plan.conditions().effectiveWeddingDate()).isEqualTo(LocalDate.of(2027, 10, 9));
        assertThat(plan.conditions().estimatedDate()).isFalse();

        // 정책 기본 예산대 · 경계 미정
        assertThat(plan.sdmBand().code()).isEqualTo("STANDARD");
        assertThat(plan.sdmBand().lowerAmount()).isNull();
        assertThat(plan.sdmBand().upperAmount()).isNull();

        // 예식장만 계산, 스드메 · 합계는 미정(null)
        assertThat(plan.estimate().calculationStatus()).isEqualTo(CalculationStatus.PARTIAL);
        assertThat(plan.estimate().venue().min()).isEqualByComparingTo("20000000");
        assertThat(plan.estimate().venue().max()).isEqualByComparingTo("30000000");
        assertThat(plan.estimate().venue().sourceType()).isEqualTo("HALL_DATA");
        assertThat(plan.estimate().sdm().min()).isNull();
        assertThat(plan.estimate().sdm().max()).isNull();
        assertThat(plan.estimate().total().min()).isNull();
        assertThat(plan.estimate().total().max()).isNull();

        EstimateRun run = estimateRunRepository.findById(plan.estimate().estimateId()).orElseThrow();
        assertThat(run.getPlanId()).isEqualTo(plan.planId());
        assertThat(jdbc.queryForObject(
                "select p.status from estimate_runs e join calculation_policies p on p.id = e.policy_id where e.id = ?",
                String.class, run.getId())).isEqualTo("DRAFT");

        // 로드맵 6단계: 예식장 단계에만 예상 비용, 스드메 단계는 미정
        assertThat(plan.steps()).extracting(PlanResponse.Step::code).containsExactly(
                "BUDGET_AGREEMENT", "VENUE_CONTRACT", "SDM_CONTRACT",
                "SHOOTING_TRAVEL", "INVITATIONS_GUESTS", "FINAL_CHECK");
        PlanResponse.Step venue = step(plan, "VENUE_CONTRACT");
        assertThat(venue.estimated().min()).isEqualByComparingTo("20000000");
        assertThat(venue.recommendedStartDate()).isEqualTo(LocalDate.of(2027, 10, 9).minusDays(300));
        assertThat(venue.recommendedEndDate()).isEqualTo(LocalDate.of(2027, 10, 9).minusDays(240));
        assertThat(step(plan, "SDM_CONTRACT").estimated().min()).isNull();
        assertThat(step(plan, "BUDGET_AGREEMENT").estimated()).isNull();

        // 기본 할 일 템플릿이 있으면 단계마다 미완료 할 일이 생긴다
        assertThat(jdbc.queryForObject(
                "select count(*) from plan_todos t join plan_steps s on s.id = t.plan_step_id "
                        + "where s.plan_id = ? and t.is_completed = false",
                Integer.class, plan.planId())).isEqualTo(2);
    }

    @Test
    void 예식장도_계산할_수_없으면_UNAVAILABLE로_저장한다() {
        when(estimateService.calculateHallCostEstimate(any())).thenReturn(Optional.empty());

        PlanResponse plan = planService.create(GROOM, request(WeddingDateType.UNDECIDED, null, null));

        assertThat(plan.estimate().calculationStatus()).isEqualTo(CalculationStatus.UNAVAILABLE);
        assertThat(plan.estimate().venue().min()).isNull();
        assertThat(step(plan, "VENUE_CONTRACT").estimated().min()).isNull();
    }

    @Test
    void 계획이_있는_회원은_새_계획을_만들_수_없다() {
        planService.create(GROOM, request(WeddingDateType.EXACT, LocalDate.of(2027, 10, 9), null));
        flushAndClear();

        assertThatThrownBy(() -> planService.create(GROOM, request(WeddingDateType.UNDECIDED, null, null)))
                .isInstanceOf(PlanAlreadyExistsException.class);
    }

    @Test
    void 선택_범위를_벗어난_예식_시기는_저장하지_않는다() {
        // 고정 시계 기준 오늘 2026-11-24 → 2026-11 ~ 2028-11만 허용 (E-15)
        assertThatThrownBy(() -> planService.create(GROOM, request(WeddingDateType.EXACT, LocalDate.of(2028, 12, 2), null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("예식 시기는 이번 달부터 2년 뒤까지 선택할 수 있어요.");
        assertThatThrownBy(() -> planService.getMine(GROOM)).isInstanceOf(PlanNotFoundException.class);

        planService.create(GROOM, request(WeddingDateType.EXACT, LocalDate.of(2027, 10, 9), null));
        flushAndClear();
        assertThatThrownBy(() -> planService.overwriteMine(GROOM, new PlanRequest(
                "PLAN_TEST_REGION", "FROM_150_TO_200", WeddingDateType.MONTH,
                null, 2026, 10, null, List.of(), null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 계획_중복이_아닌_무결성_오류는_409로_숨기지_않는다() {
        // 토큰은 유효하지만 회원 행이 없는 경우 → fk_plans_owner 위반
        UUID deletedUser = UUID.fromString("00000000-0000-0000-0000-0000000000ff");

        assertThatThrownBy(() -> planService.create(deletedUser, request(WeddingDateType.UNDECIDED, null, null)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .isNotInstanceOf(PlanAlreadyExistsException.class);
    }

    @Test
    void 계획이_없으면_조회와_덮어쓰기는_404다() {
        assertThatThrownBy(() -> planService.getMine(STRANGER)).isInstanceOf(PlanNotFoundException.class);
        assertThatThrownBy(() -> planService.overwriteMine(STRANGER, request(WeddingDateType.UNDECIDED, null, null)))
                .isInstanceOf(PlanNotFoundException.class);
    }

    @Test
    void 합류한_짝꿍도_같은_준비_공간을_보고_덮어쓸_수_있다() {
        PlanResponse created = planService.create(GROOM, request(WeddingDateType.EXACT, LocalDate.of(2027, 10, 9), null));
        jdbc.update("insert into plan_members (plan_id, user_id, role) values (?, ?, 'PARTNER')",
                created.planId(), BRIDE);
        flushAndClear();

        PlanResponse seenByBride = planService.getMine(BRIDE);
        assertThat(seenByBride.planId()).isEqualTo(created.planId());
        assertThat(seenByBride.role()).isEqualTo("PARTNER");

        PlanResponse overwritten = planService.overwriteMine(BRIDE, request(WeddingDateType.EXACT, LocalDate.of(2027, 10, 9), "PREMIUM"));
        assertThat(overwritten.planId()).isEqualTo(created.planId());
        assertThat(overwritten.updatedBy()).isEqualTo(BRIDE);
    }

    @Test
    void 조건을_바꾸면_완료한_단계와_할_일은_유지하고_미완료_단계의_예상_비용만_갱신한다() {
        PlanResponse created = planService.create(GROOM, request(WeddingDateType.EXACT, LocalDate.of(2027, 10, 9), null));
        UUID venueStepId = step(created, "VENUE_CONTRACT").stepId();
        UUID sdmStepId = step(created, "SDM_CONTRACT").stepId();
        flushAndClear();

        // 예식장 단계 완료 + 실제 계약 금액, 예식장 할 일 완료 (로드맵 API가 하는 일을 직접 반영)
        jdbc.update("update plan_steps set actual_amount = 25000000, completed_at = now(), completed_by = ? where id = ?",
                GROOM, venueStepId);
        jdbc.update("update plan_todos set is_completed = true, completed_at = now() where plan_step_id = ?",
                venueStepId);
        jdbc.update("update plans set schedule_updated_at = schedule_updated_at - interval '1 day' where id = ?",
                created.planId());

        hallEstimateReturns("40000000", "50000000");
        PlanResponse overwritten = planService.overwriteMine(GROOM, new PlanRequest(
                "PLAN_TEST_REGION", "FROM_250_TO_300", WeddingDateType.MONTH,
                null, 2028, 3, null, List.of("ANY"), "PREMIUM"));
        flushAndClear();
        PlanResponse plan = planService.getMine(GROOM);

        // 같은 계획에 덮어쓰기, 새 계산 기록으로 교체
        assertThat(plan.planId()).isEqualTo(created.planId());
        assertThat(plan.estimate().estimateId()).isNotEqualTo(created.estimate().estimateId());
        assertThat(estimateRunRepository.findById(created.estimate().estimateId())).isPresent();
        assertThat(plan.estimate().venue().min()).isEqualByComparingTo("40000000");

        // 조건 · 예산대 변경 반영
        assertThat(plan.conditions().guestRangeCode()).isEqualTo("FROM_250_TO_300");
        assertThat(plan.conditions().weddingDateType()).isEqualTo(WeddingDateType.MONTH);
        assertThat(plan.conditions().weddingYear()).isEqualTo(2028);
        assertThat(plan.conditions().weddingMonth()).isEqualTo(3);
        assertThat(plan.conditions().effectiveWeddingDate()).isEqualTo(LocalDate.of(2028, 3, 1));
        assertThat(plan.conditions().estimatedDate()).isTrue();
        assertThat(plan.conditions().budget()).isNull();
        assertThat(plan.conditions().moods()).isEmpty();
        assertThat(plan.sdmBand().code()).isEqualTo("PREMIUM");
        assertThat(overwritten.sdmBand().code()).isEqualTo("PREMIUM");

        // 완료한 예식장 단계: 이전 예상 비용 · 실제 금액 · 완료 유지, 권장 기간만 새 예식일 기준
        PlanResponse.Step venue = step(plan, "VENUE_CONTRACT");
        assertThat(venue.completed()).isTrue();
        assertThat(venue.actualAmount()).isEqualByComparingTo("25000000");
        assertThat(venue.estimated().min()).isEqualByComparingTo("20000000");
        assertThat(venue.estimated().max()).isEqualByComparingTo("30000000");
        assertThat(venue.recommendedStartDate()).isEqualTo(LocalDate.of(2028, 3, 1).minusDays(300));

        // 미완료 스드메 단계: 새 계산 기록을 근거로 갱신 (금액은 아직 미정)
        assertThat(jdbc.queryForObject("select estimate_run_id from plan_steps where id = ?", UUID.class, sdmStepId))
                .isEqualTo(plan.estimate().estimateId());
        assertThat(step(plan, "SDM_CONTRACT").estimated().min()).isNull();

        // 할 일 완료 기록 유지
        assertThat(jdbc.queryForObject(
                "select is_completed from plan_todos where plan_step_id = ?", Boolean.class, venueStepId)).isTrue();
        assertThat(jdbc.queryForObject(
                "select is_completed from plan_todos where plan_step_id = ?", Boolean.class, sdmStepId)).isFalse();

        // 예식일이 바뀌었으므로 일정 변경 시각도 갱신 (R-07)
        assertThat(plan.scheduleUpdatedAt()).isAtSameInstantAs(NOW.atOffset(ZoneOffset.UTC));
    }

    @Test
    void 예산대만_바꾸면_예산대가_반영되고_예식_일정_변경_시각은_그대로다() {
        PlanResponse created = planService.create(GROOM, request(WeddingDateType.EXACT, LocalDate.of(2027, 10, 9), null));
        flushAndClear();
        jdbc.update("update plans set schedule_updated_at = schedule_updated_at - interval '1 day' where id = ?",
                created.planId());
        PlanResponse before = planService.getMine(GROOM);

        PlanResponse after = planService.overwriteMine(GROOM, request(WeddingDateType.EXACT, LocalDate.of(2027, 10, 9), "LUXURY"));
        flushAndClear();
        PlanResponse plan = planService.getMine(GROOM);

        assertThat(after.sdmBand().code()).isEqualTo("LUXURY");
        assertThat(plan.sdmBand().code()).isEqualTo("LUXURY");
        assertThat(plan.scheduleUpdatedAt()).isEqualTo(before.scheduleUpdatedAt());
        assertThat(plan.conditionsUpdatedAt()).isAfter(before.scheduleUpdatedAt());
    }

    @Test
    void 예산대를_생략하고_덮어쓰면_지금_예산대를_유지한다() {
        planService.create(GROOM, request(WeddingDateType.EXACT, LocalDate.of(2027, 10, 9), "BASIC"));
        flushAndClear();

        PlanResponse plan = planService.overwriteMine(GROOM, request(WeddingDateType.UNDECIDED, null, null));

        assertThat(plan.sdmBand().code()).isEqualTo("BASIC");
    }

    @Test
    void 예식_시기_미정이면_오늘부터_12개월_뒤를_기준으로_하고_계산은_그_뒤_첫_토요일로_한다() {
        PlanResponse plan = planService.create(GROOM, request(WeddingDateType.UNDECIDED, null, null));

        assertThat(plan.conditions().effectiveWeddingDate()).isEqualTo(LocalDate.of(2027, 11, 24));
        assertThat(plan.conditions().estimatedDate()).isTrue();

        ArgumentCaptor<EstimateRequest> captor = ArgumentCaptor.forClass(EstimateRequest.class);
        verify(estimateService, atLeastOnce()).calculateHallCostEstimate(captor.capture());
        assertThat(captor.getValue().weddingDate()).isEqualTo(LocalDate.of(2027, 11, 27));
    }

    @Test
    @Sql({"/sql/plan-fixture.sql", "/sql/plan-active-policy-fixture.sql"})
    void ACTIVE_정책이_있으면_그_정책의_예산대를_쓰되_스드메_예상_비용은_여전히_미정이다() {
        PlanResponse plan = planService.create(GROOM, request(WeddingDateType.EXACT, LocalDate.of(2027, 10, 9), null));

        assertThat(plan.sdmBand().code()).isEqualTo("STANDARD");
        assertThat(plan.sdmBand().lowerAmount()).isEqualByComparingTo("2500000");
        assertThat(plan.sdmBand().upperAmount()).isEqualByComparingTo("3500000");
        assertThat(plan.estimate().sdm().min()).isNull();
        assertThat(plan.estimate().calculationStatus()).isEqualTo(CalculationStatus.PARTIAL);
        flushAndClear();
        assertThat(jdbc.queryForObject(
                "select p.version from estimate_runs e join calculation_policies p on p.id = e.policy_id where e.id = ?",
                String.class, plan.estimate().estimateId())).isEqualTo("plan-test-active");
    }

    private void hallEstimateReturns(String min, String max) {
        when(estimateService.calculateHallCostEstimate(any())).thenReturn(Optional.of(new HallCostEstimate(
                new BigDecimal(min), new BigDecimal(max), "HALL_DATA", null, null, null, 12)));
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    private static PlanRequest request(WeddingDateType type, LocalDate date, String bandCode) {
        return new PlanRequest(
                "PLAN_TEST_REGION", "FROM_150_TO_200", type,
                date, null, null,
                30_000_000L, List.of("BRIGHT", "CLASSIC"), bandCode);
    }

    private static PlanResponse.Step step(PlanResponse plan, String code) {
        return plan.steps().stream().filter(s -> s.code().equals(code)).findFirst().orElseThrow();
    }
}
