package com.wedid.backend.roadmap;

import com.wedid.backend.estimate.dto.EstimateRequest.WeddingDateType;
import com.wedid.backend.estimate.dto.HallCostEstimate;
import com.wedid.backend.estimate.service.EstimateService;
import com.wedid.backend.plan.dto.PlanRequest;
import com.wedid.backend.plan.dto.PlanResponse;
import com.wedid.backend.plan.service.PlanEstimateRecorder;
import com.wedid.backend.plan.service.PlanNotFoundException;
import com.wedid.backend.plan.service.PlanPolicyResolver;
import com.wedid.backend.plan.service.PlanService;
import com.wedid.backend.roadmap.dto.RoadmapResponse;
import com.wedid.backend.roadmap.dto.TodoResponse;
import com.wedid.backend.roadmap.dto.TodoUpdateRequest;
import com.wedid.backend.roadmap.service.RoadmapService;
import com.wedid.backend.sdm.repository.SdmBasePriceRepository;
import com.wedid.backend.sdm.service.SdmEstimateCalculator;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
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
import static org.mockito.Mockito.when;

/**
 * 로컬 PostgreSQL에 대해 GET /plans/me/roadmap 조회를 검증한다.
 * 할 일 템플릿 콘텐츠는 미정이라 테스트용 할 일(plan-fixture.sql · roadmap-fixture.sql)만 쓴다. 각 테스트는 롤백된다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({RoadmapService.class, PlanService.class, PlanPolicyResolver.class, PlanEstimateRecorder.class,
        SdmEstimateCalculator.class, SdmBasePriceRepository.class,
        RoadmapQueryIntegrationTest.TestConfig.class})
@Sql({"/sql/draft-policy-only.sql", "/sql/plan-fixture.sql", "/sql/roadmap-fixture.sql"})
class RoadmapQueryIntegrationTest {

    static final UUID GROOM = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    static final UUID BRIDE = UUID.fromString("00000000-0000-0000-0000-0000000000a2");

    /** 2026-11-24 12:00 (Asia/Seoul) */
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
    private RoadmapService roadmapService;

    @Autowired
    private PlanService planService;

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbc;

    @MockitoBean
    private EstimateService estimateService;

    @BeforeEach
    void setUp() {
        when(estimateService.calculateHallCostEstimate(any())).thenReturn(Optional.of(new HallCostEstimate(
                new BigDecimal("20000000"), new BigDecimal("30000000"), "HALL_DATA", null, null, null, 12)));
    }

    @Test
    void 여섯_단계와_단계별_할_일을_로드맵_순서로_돌려준다() {
        PlanResponse plan = createPlan(LocalDate.of(2027, 10, 9));

        RoadmapResponse roadmap = roadmapService.getRoadmap(GROOM);

        assertThat(roadmap.planId()).isEqualTo(plan.planId());
        assertThat(roadmap.steps()).extracting(RoadmapResponse.Step::code).containsExactly(
                "BUDGET_AGREEMENT", "VENUE_CONTRACT", "SDM_CONTRACT",
                "SHOOTING_TRAVEL", "INVITATIONS_GUESTS", "FINAL_CHECK");

        RoadmapResponse.Step venue = step(roadmap, "VENUE_CONTRACT");
        assertThat(venue.stepId()).isEqualTo(planStepId(plan, "VENUE_CONTRACT"));
        assertThat(venue.sortOrder()).isEqualTo(2);
        assertThat(venue.costKind()).isEqualTo("VENUE");
        assertThat(venue.daysBeforeStart()).isEqualTo(300);
        assertThat(venue.daysBeforeEnd()).isEqualTo(240);
        assertThat(venue.recommendedStartDate()).isEqualTo(LocalDate.of(2027, 10, 9).minusDays(300));
        assertThat(venue.recommendedEndDate()).isEqualTo(LocalDate.of(2027, 10, 9).minusDays(240));
        assertThat(venue.estimated().min()).isEqualByComparingTo("20000000");
        assertThat(venue.estimated().max()).isEqualByComparingTo("30000000");
        assertThat(venue.actualAmount()).isNull();
        assertThat(venue.completed()).isFalse();

        // 단계 안 할 일은 템플릿 순서(sort_order)대로, taskId로 바로 PATCH할 수 있다
        assertThat(venue.todos()).extracting(TodoResponse::title)
                .containsExactly("테스트: 예식장 예산 상한 정하기", "테스트: 예식장 투어 예약");
        assertThat(venue.todos()).allSatisfy(todo -> {
            assertThat(todo.taskId()).isNotNull();
            assertThat(todo.stepId()).isEqualTo(venue.stepId());
            assertThat(todo.completed()).isFalse();
            assertThat(todo.assignee()).isEqualTo("UNASSIGNED");
        });
        assertThat(step(roadmap, "SDM_CONTRACT").todos()).extracting(TodoResponse::title)
                .containsExactly("테스트: 스드메 상담 예약");
        assertThat(step(roadmap, "FINAL_CHECK").todos()).isEmpty();

        // 금액 단계가 아니면 예상 비용 없음, 스드메는 미정(null)
        assertThat(step(roadmap, "BUDGET_AGREEMENT").estimated()).isNull();
        assertThat(step(roadmap, "SDM_CONTRACT").estimated().min()).isNull();
        assertThat(step(roadmap, "SDM_CONTRACT").estimated().max()).isNull();
    }

    @Test
    void 템플릿_데이터가_없는_필드는_자리만_두고_비워_보낸다() {
        createPlan(LocalDate.of(2027, 10, 9));

        RoadmapResponse.Step venue = step(roadmapService.getRoadmap(GROOM), "VENUE_CONTRACT");

        assertThat(venue.decisionItems()).isEmpty();
        assertThat(venue.guidanceItems()).isEmpty();
        assertThat(venue.shortTitle()).isNull();
        assertThat(venue.costNote()).isNull();
    }

    @Test
    void 정해야_할_것_알아두면_좋은_점은_템플릿_JSON_배열에서_읽는다() {
        // 콘텐츠는 아직 미정이라 테스트 안에서만 예시 문구를 넣는다(롤백)
        jdbc.update("update roadmap_step_templates set decision_items = '[\"예식 날짜, 시간대\", \"보증 인원\"]'::jsonb, "
                + "guidance_items = '[\"계약금 환급 기준 확인\"]'::jsonb where code = 'VENUE_CONTRACT'");
        createPlan(LocalDate.of(2027, 10, 9));

        RoadmapResponse.Step venue = step(roadmapService.getRoadmap(GROOM), "VENUE_CONTRACT");

        assertThat(venue.decisionItems()).containsExactly("예식 날짜, 시간대", "보증 인원");
        assertThat(venue.guidanceItems()).containsExactly("계약금 환급 기준 확인");
    }

    @Test
    void D_day와_다음_단계_완료_단계_수를_계산한다() {
        PlanResponse plan = createPlan(LocalDate.of(2027, 10, 9));

        RoadmapResponse before = roadmapService.getRoadmap(GROOM);
        assertThat(before.wedding().weddingDateType()).isEqualTo(WeddingDateType.EXACT);
        assertThat(before.wedding().effectiveWeddingDate()).isEqualTo(LocalDate.of(2027, 10, 9));
        assertThat(before.wedding().estimatedDate()).isFalse();
        assertThat(before.wedding().dDay()).isEqualTo(319);   // 2026-11-24 → 2027-10-09
        assertThat(before.nextStepId()).isEqualTo(planStepId(plan, "BUDGET_AGREEMENT"));
        assertThat(before.completedStepCount()).isZero();

        // 순서와 상관없이 완료 가능, 다음 단계는 가장 이른 미완료 단계 (R-02)
        roadmapService.updateStepCompletion(GROOM, planStepId(plan, "BUDGET_AGREEMENT"), true);
        roadmapService.updateStepCompletion(GROOM, planStepId(plan, "SHOOTING_TRAVEL"), true);
        flushAndClear();

        RoadmapResponse after = roadmapService.getRoadmap(GROOM);
        assertThat(after.nextStepId()).isEqualTo(planStepId(plan, "VENUE_CONTRACT"));
        assertThat(after.completedStepCount()).isEqualTo(2);
        assertThat(step(after, "BUDGET_AGREEMENT").completed()).isTrue();
        assertThat(step(after, "BUDGET_AGREEMENT").completedBy()).isEqualTo(GROOM);
    }

    @Test
    void 예식_시기를_월만_정하면_그_달_1일_기준_예상_D_day다() {
        planService.create(GROOM, new PlanRequest("PLAN_TEST_REGION", "FROM_150_TO_200", WeddingDateType.MONTH,
                null, 2027, 10, 30_000_000L, List.of("BRIGHT"), null));
        flushAndClear();

        RoadmapResponse.Wedding wedding = roadmapService.getRoadmap(GROOM).wedding();

        assertThat(wedding.weddingDateType()).isEqualTo(WeddingDateType.MONTH);
        assertThat(wedding.effectiveWeddingDate()).isEqualTo(LocalDate.of(2027, 10, 1));
        assertThat(wedding.estimatedDate()).isTrue();
        assertThat(wedding.dDay()).isEqualTo(311);
    }

    @Test
    void 권장_기간이_지난_미완료_단계는_overdue다() {
        // 예식일이 가까우면 앞 단계들의 권장 기간이 이미 지났다
        PlanResponse plan = createPlan(LocalDate.of(2027, 1, 9));
        roadmapService.updateStepCompletion(GROOM, planStepId(plan, "SHOOTING_TRAVEL"), true);
        flushAndClear();

        RoadmapResponse roadmap = roadmapService.getRoadmap(GROOM);

        assertThat(step(roadmap, "BUDGET_AGREEMENT").overdue()).isTrue();   // 권장 끝 2026-03-15
        assertThat(step(roadmap, "SHOOTING_TRAVEL").overdue()).isFalse();   // 지났지만 완료
        assertThat(step(roadmap, "INVITATIONS_GUESTS").overdue()).isFalse(); // 권장 끝 2026-12-10
        assertThat(step(roadmap, "FINAL_CHECK").overdue()).isFalse();
        assertThat(step(roadmap, "BUDGET_AGREEMENT").overdueReviewed()).isFalse();
    }

    @Test
    void 지금_예식_일정_기준으로_지난_단계를_확인했으면_overdueReviewed다() {
        PlanResponse plan = createPlan(LocalDate.of(2027, 1, 9));
        UUID agreement = planStepId(plan, "BUDGET_AGREEMENT");
        UUID venue = planStepId(plan, "VENUE_CONTRACT");
        // 처리 API는 아직 없으므로 DB에 직접 기록한다: 하나는 지금 일정 기준, 하나는 예전 일정 기준
        jdbc.update("update plan_steps s set overdue_reviewed_for = p.schedule_updated_at "
                + "from plans p where p.id = s.plan_id and s.id = ?", agreement);
        jdbc.update("update plan_steps s set overdue_reviewed_for = p.schedule_updated_at - interval '1 day' "
                + "from plans p where p.id = s.plan_id and s.id = ?", venue);
        flushAndClear();

        RoadmapResponse roadmap = roadmapService.getRoadmap(GROOM);

        assertThat(step(roadmap, "BUDGET_AGREEMENT").overdueReviewed()).isTrue();
        assertThat(step(roadmap, "VENUE_CONTRACT").overdueReviewed()).isFalse();
        assertThat(roadmap.scheduleUpdatedAt()).isNotNull();
    }

    @Test
    void 멤버와_담당은_조회한_사람_기준이다() {
        PlanResponse plan = createPlan(LocalDate.of(2027, 10, 9));
        RoadmapResponse alone = roadmapService.getRoadmap(GROOM);
        assertThat(alone.me().userId()).isEqualTo(GROOM);
        assertThat(alone.me().displayName()).isEqualTo("신랑");
        assertThat(alone.me().role()).isEqualTo("OWNER");
        assertThat(alone.me().loginProvider()).isEqualTo("KAKAO");
        assertThat(alone.partner()).isNull();

        jdbc.update("insert into plan_members (plan_id, user_id, role) values (?, ?, 'PARTNER')", plan.planId(), BRIDE);
        UUID taskId = step(alone, "VENUE_CONTRACT").todos().get(0).taskId();
        roadmapService.updateTodo(GROOM, taskId, new TodoUpdateRequest(true, "PARTNER"));
        flushAndClear();

        RoadmapResponse byGroom = roadmapService.getRoadmap(GROOM);
        assertThat(byGroom.partner().userId()).isEqualTo(BRIDE);
        assertThat(byGroom.partner().role()).isEqualTo("PARTNER");
        assertThat(byGroom.partner().loginProvider()).isEqualTo("GOOGLE");
        assertThat(todo(byGroom, taskId).assignee()).isEqualTo("PARTNER");
        assertThat(todo(byGroom, taskId).completed()).isTrue();

        RoadmapResponse byBride = roadmapService.getRoadmap(BRIDE);
        assertThat(byBride.planId()).isEqualTo(plan.planId());
        assertThat(byBride.me().userId()).isEqualTo(BRIDE);
        assertThat(byBride.me().displayName()).isEqualTo("신부");
        assertThat(byBride.partner().userId()).isEqualTo(GROOM);
        assertThat(byBride.partner().role()).isEqualTo("OWNER");
        assertThat(todo(byBride, taskId).assignee()).isEqualTo("ME");
        assertThat(todo(byBride, taskId).updatedBy()).isEqualTo(GROOM);
    }

    @Test
    void 로드맵_조회_쿼리_수는_단계와_할_일_수에_따라_늘지_않는다() {
        PlanResponse plan = createPlan(LocalDate.of(2027, 10, 9));
        jdbc.update("insert into plan_members (plan_id, user_id, role) values (?, ?, 'PARTNER')", plan.planId(), BRIDE);
        // 모든 단계에 할 일을 3개씩 더 둔다
        jdbc.update("""
                insert into todo_templates (step_template_id, code, title, sort_order)
                select st.id, 'QUERY_COUNT_TEST_' || st.id || '_' || n, '테스트: 할 일 ' || n, 33000 + n
                from roadmap_step_templates st cross join generate_series(1, 3) n
                """);
        jdbc.update("""
                insert into plan_todos (id, plan_step_id, todo_template_id, title_snapshot,
                                        assignment_kind, is_completed, updated_by)
                select gen_random_uuid(), ps.id, tt.id, tt.title, 'UNASSIGNED', false, ?
                from plan_steps ps
                join todo_templates tt on tt.step_template_id = ps.step_template_id and tt.code like 'QUERY_COUNT_TEST_%'
                where ps.plan_id = ?
                """, GROOM, plan.planId());
        flushAndClear();

        Statistics statistics = entityManager.getEntityManager().getEntityManagerFactory()
                .unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
        statistics.clear();

        RoadmapResponse roadmap = roadmapService.getRoadmap(GROOM);

        assertThat(roadmap.steps()).allSatisfy(step -> assertThat(step.todos()).hasSizeGreaterThanOrEqualTo(3));
        // 회원의 계획 멤버, 계획, 멤버 목록, 단계 템플릿, 단계, 할 일, 할 일 템플릿, 회원, 로그인 계정 = 9
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(9);
    }

    @Test
    void 계획이_없으면_404다() {
        assertThatThrownBy(() -> roadmapService.getRoadmap(BRIDE))
                .isInstanceOf(PlanNotFoundException.class);
    }

    private PlanResponse createPlan(LocalDate weddingDate) {
        PlanResponse plan = planService.create(GROOM, new PlanRequest(
                "PLAN_TEST_REGION", "FROM_150_TO_200", WeddingDateType.EXACT,
                weddingDate, null, null, 30_000_000L, List.of("BRIGHT"), null));
        flushAndClear();
        return plan;
    }

    private static UUID planStepId(PlanResponse plan, String code) {
        return plan.steps().stream().filter(s -> s.code().equals(code)).findFirst().orElseThrow().stepId();
    }

    private static RoadmapResponse.Step step(RoadmapResponse roadmap, String code) {
        return roadmap.steps().stream().filter(s -> s.code().equals(code)).findFirst().orElseThrow();
    }

    private static TodoResponse todo(RoadmapResponse roadmap, UUID taskId) {
        return roadmap.steps().stream().flatMap(s -> s.todos().stream())
                .filter(t -> t.taskId().equals(taskId)).findFirst().orElseThrow();
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}
