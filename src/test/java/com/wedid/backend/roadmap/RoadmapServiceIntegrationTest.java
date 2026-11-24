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
import com.wedid.backend.roadmap.dto.BudgetStatusResponse;
import com.wedid.backend.roadmap.dto.BudgetStatusResponse.SkipReason;
import com.wedid.backend.roadmap.dto.BudgetStatusResponse.Verdict;
import com.wedid.backend.roadmap.dto.RoadmapStepResponse;
import com.wedid.backend.roadmap.dto.TodoResponse;
import com.wedid.backend.roadmap.dto.TodoUpdateRequest;
import com.wedid.backend.roadmap.service.RoadmapItemNotFoundException;
import com.wedid.backend.roadmap.service.RoadmapService;
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
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 로컬 PostgreSQL(docker compose)에 대해 할 일 · 실제 계약 금액 · 단계 완료 · 남은 예산 판정을 검증한다.
 * sql/034 시드(DRAFT 정책 · 금액 없는 예산대 · 로드맵 6단계)와 테스트용 할 일(plan-fixture.sql)을 쓴다.
 * 예식장 금액 계산은 고정값으로 대체한다. 각 테스트는 끝나면 롤백된다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({RoadmapService.class, PlanService.class, PlanPolicyResolver.class, PlanEstimateRecorder.class,
        RoadmapServiceIntegrationTest.TestConfig.class})
@Sql("/sql/plan-fixture.sql")
class RoadmapServiceIntegrationTest {

    static final UUID GROOM = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    static final UUID BRIDE = UUID.fromString("00000000-0000-0000-0000-0000000000a2");
    static final UUID STRANGER = UUID.fromString("00000000-0000-0000-0000-0000000000a3");

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

    private PlanResponse plan;

    @BeforeEach
    void setUp() {
        hallEstimateReturns("20000000", "30000000");
        plan = planService.create(GROOM, request(30_000_000L));
        flushAndClear();
    }

    // ---- 할 일 ----

    @Test
    void 할_일을_체크하고_해제하면_체크_시각과_마지막_수정자가_남는다() {
        joinBride();
        UUID taskId = todoOf("VENUE_CONTRACT");

        TodoResponse checked = roadmapService.updateTodo(BRIDE, taskId, new TodoUpdateRequest(true, null));
        flushAndClear();
        assertThat(checked.completed()).isTrue();
        assertThat(checked.completedAt()).isEqualTo(OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC));
        assertThat(checked.updatedBy()).isEqualTo(BRIDE);
        assertThat(todoRow(taskId)).containsEntry("is_completed", true).containsEntry("updated_by", BRIDE);

        TodoResponse unchecked = roadmapService.updateTodo(GROOM, taskId, new TodoUpdateRequest(false, null));
        flushAndClear();
        assertThat(unchecked.completed()).isFalse();
        assertThat(unchecked.completedAt()).isNull();
        assertThat(unchecked.updatedBy()).isEqualTo(GROOM);
        assertThat(todoRow(taskId)).containsEntry("is_completed", false).containsEntry("completed_at", null);
    }

    @Test
    void 담당은_회원_ID로_저장하고_보는_사람_기준으로_돌려준다() {
        joinBride();
        UUID taskId = todoOf("VENUE_CONTRACT");

        TodoResponse byGroom = roadmapService.updateTodo(GROOM, taskId, new TodoUpdateRequest(null, "PARTNER"));
        flushAndClear();
        assertThat(byGroom.assignee()).isEqualTo("PARTNER");
        assertThat(byGroom.assignedUserId()).isEqualTo(BRIDE);
        assertThat(todoRow(taskId)).containsEntry("assignment_kind", "USER").containsEntry("assigned_user_id", BRIDE);

        // 같은 할 일을 신부가 보면 "나"
        TodoResponse byBride = roadmapService.updateTodo(BRIDE, taskId, new TodoUpdateRequest(true, null));
        assertThat(byBride.assignee()).isEqualTo("ME");

        TodoResponse me = roadmapService.updateTodo(GROOM, taskId, new TodoUpdateRequest(null, "ME"));
        assertThat(me.assignee()).isEqualTo("ME");
        assertThat(me.assignedUserId()).isEqualTo(GROOM);

        TodoResponse both = roadmapService.updateTodo(GROOM, taskId, new TodoUpdateRequest(null, "BOTH"));
        flushAndClear();
        assertThat(both.assignee()).isEqualTo("BOTH");
        assertThat(both.assignedUserId()).isNull();
        assertThat(todoRow(taskId)).containsEntry("assignment_kind", "BOTH").containsEntry("assigned_user_id", null);

        TodoResponse none = roadmapService.updateTodo(GROOM, taskId, new TodoUpdateRequest(null, "UNASSIGNED"));
        assertThat(none.assignee()).isEqualTo("UNASSIGNED");
    }

    @Test
    void 짝꿍이_없으면_짝꿍에게_맡길_수_없다() {
        UUID taskId = todoOf("VENUE_CONTRACT");

        assertThatThrownBy(() -> roadmapService.updateTodo(GROOM, taskId, new TodoUpdateRequest(null, "PARTNER")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("연결된 짝꿍이 없어요");
    }

    @Test
    void 계획을_떠난_회원이_담당이면_지금_짝꿍으로_표시하지_않는다() {
        joinBride();
        UUID taskId = todoOf("VENUE_CONTRACT");
        roadmapService.updateTodo(GROOM, taskId, new TodoUpdateRequest(null, "PARTNER"));
        flushAndClear();

        // 연결 해제: 짝꿍 멤버십만 지우고 담당 기록은 남는다(P-10)
        jdbc.update("delete from plan_members where user_id = ?", BRIDE);

        TodoResponse after = roadmapService.updateTodo(GROOM, taskId, new TodoUpdateRequest(true, null));
        assertThat(after.assignee()).isEqualTo("FORMER_MEMBER");
        assertThat(after.assignedUserId()).isEqualTo(BRIDE);
    }

    @Test
    void 다른_계획의_할_일과_단계는_찾지_않는다() {
        UUID taskId = todoOf("VENUE_CONTRACT");
        UUID stepId = stepOf("VENUE_CONTRACT");
        planService.create(STRANGER, request(30_000_000L));
        flushAndClear();

        assertThatThrownBy(() -> roadmapService.updateTodo(STRANGER, taskId, new TodoUpdateRequest(true, null)))
                .isInstanceOf(RoadmapItemNotFoundException.class);
        assertThatThrownBy(() -> roadmapService.updateStepCompletion(STRANGER, stepId, true))
                .isInstanceOf(RoadmapItemNotFoundException.class);
        assertThatThrownBy(() -> roadmapService.recordContractAmount(STRANGER, stepId, 1L))
                .isInstanceOf(RoadmapItemNotFoundException.class);
        assertThat(todoRow(taskId)).containsEntry("is_completed", false);
    }

    @Test
    void 계획이_없으면_404다() {
        // 신부는 아직 합류하지 않아 계획이 없다
        assertThatThrownBy(() -> roadmapService.getBudgetStatus(BRIDE))
                .isInstanceOf(PlanNotFoundException.class);
        assertThatThrownBy(() -> roadmapService.updateStepCompletion(BRIDE, stepOf("VENUE_CONTRACT"), true))
                .isInstanceOf(PlanNotFoundException.class);
    }

    // ---- 실제 계약 금액 · 단계 완료 ----

    @Test
    void 금액_단계는_실제_금액을_넣어야_완료할_수_있다() {
        UUID venue = stepOf("VENUE_CONTRACT");

        assertThatThrownBy(() -> roadmapService.updateStepCompletion(GROOM, venue, true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("실제 계약 금액을 입력해야 단계를 완료할 수 있어요.");

        RoadmapStepResponse withAmount = roadmapService.recordContractAmount(GROOM, venue, 25_000_000L);
        assertThat(withAmount.actualAmount()).isEqualByComparingTo("25000000");
        assertThat(withAmount.completed()).isFalse();

        joinBride();
        RoadmapStepResponse done = roadmapService.updateStepCompletion(BRIDE, venue, true);
        flushAndClear();
        assertThat(done.completed()).isTrue();
        assertThat(done.completedBy()).isEqualTo(BRIDE);
        assertThat(done.updatedBy()).isEqualTo(BRIDE);
        assertThat(stepRow(venue)).containsEntry("completed_by", BRIDE).containsEntry("updated_by", BRIDE);
    }

    @Test
    void 금액이_없는_단계는_금액_없이_완료하고_금액은_넣을_수_없다() {
        UUID agreement = stepOf("BUDGET_AGREEMENT");

        assertThatThrownBy(() -> roadmapService.recordContractAmount(GROOM, agreement, 1_000_000L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("실제 계약 금액은 예식장 · 스드메 단계에만 입력할 수 있어요.");

        RoadmapStepResponse done = roadmapService.updateStepCompletion(GROOM, agreement, true);
        assertThat(done.completed()).isTrue();
        assertThat(done.estimated()).isNull();
        assertThat(done.actualAmount()).isNull();
    }

    @Test
    void 이미_완료한_단계를_다시_완료해도_처음_완료_기록을_유지한다() {
        joinBride();
        UUID agreement = stepOf("BUDGET_AGREEMENT");
        roadmapService.updateStepCompletion(GROOM, agreement, true);
        flushAndClear();

        RoadmapStepResponse again = roadmapService.updateStepCompletion(BRIDE, agreement, true);
        assertThat(again.completedBy()).isEqualTo(GROOM);
        assertThat(again.updatedBy()).isEqualTo(GROOM);
    }

    @Test
    void 완료한_단계도_금액을_고칠_수_있고_예상_비용과_완료_상태는_그대로다() {
        UUID venue = stepOf("VENUE_CONTRACT");
        roadmapService.recordContractAmount(GROOM, venue, 25_000_000L);
        roadmapService.updateStepCompletion(GROOM, venue, true);
        flushAndClear();

        joinBride();
        RoadmapStepResponse edited = roadmapService.recordContractAmount(BRIDE, venue, 0L);
        flushAndClear();

        assertThat(edited.actualAmount()).isEqualByComparingTo("0");
        assertThat(edited.completed()).isTrue();
        assertThat(edited.completedBy()).isEqualTo(GROOM);
        assertThat(edited.updatedBy()).isEqualTo(BRIDE);
        assertThat(edited.estimated().min()).isEqualByComparingTo("20000000");
        assertThat(edited.estimated().max()).isEqualByComparingTo("30000000");
    }

    @Test
    void 완료를_취소하면_실제_금액은_남기고_예상_비용은_현재_계산_결과로_맞춘다() {
        UUID venue = stepOf("VENUE_CONTRACT");
        roadmapService.recordContractAmount(GROOM, venue, 25_000_000L);
        roadmapService.updateStepCompletion(GROOM, venue, true);
        flushAndClear();

        // 완료한 뒤 조건이 바뀌어 새 계산 결과가 나옴 → 완료한 단계는 예전 예상 비용 유지(P-02)
        hallEstimateReturns("40000000", "50000000");
        planService.overwriteMine(GROOM, request(30_000_000L));
        flushAndClear();
        assertThat(stepRow(venue).get("estimated_min")).isEqualTo(new BigDecimal("20000000.00"));

        RoadmapStepResponse reopened = roadmapService.updateStepCompletion(GROOM, venue, false);
        flushAndClear();

        assertThat(reopened.completed()).isFalse();
        assertThat(reopened.completedAt()).isNull();
        assertThat(reopened.completedBy()).isNull();
        assertThat(reopened.actualAmount()).isEqualByComparingTo("25000000");
        assertThat(reopened.estimated().min()).isEqualByComparingTo("40000000");
        assertThat(reopened.estimated().max()).isEqualByComparingTo("50000000");
        assertThat(stepRow(venue)).containsEntry("completed_at", null)
                .containsEntry("actual_amount", new BigDecimal("25000000"));
    }

    // ---- 남은 예산 ----

    @Test
    void 스드메_예상_비용이_미정이면_쓴_금액과_남은_예산만_주고_판정하지_않는다() {
        UUID venue = stepOf("VENUE_CONTRACT");
        roadmapService.recordContractAmount(GROOM, venue, 25_000_000L);
        // 금액만 넣고 완료하지 않으면 쓴 금액에 넣지 않는다
        BudgetStatusResponse before = roadmapService.getBudgetStatus(GROOM);
        assertThat(before.spentAmount()).isEqualByComparingTo("0");

        roadmapService.updateStepCompletion(GROOM, venue, true);
        flushAndClear();
        BudgetStatusResponse status = roadmapService.getBudgetStatus(GROOM);

        assertThat(status.budget()).isEqualByComparingTo("30000000");
        assertThat(status.spentAmount()).isEqualByComparingTo("25000000");
        assertThat(status.remainingBudget()).isEqualByComparingTo("5000000");
        assertThat(status.remainingEstimate()).isNull();
        assertThat(status.verdict()).isNull();
        assertThat(status.verdictSkipReason()).isEqualTo(SkipReason.ESTIMATE_PENDING);
        assertThat(status.amountStepsCompleted()).isFalse();

        // 단계별 금액: 예식장(완료 · 실제 금액 · 예상 비용 유지), 스드메(미완료 · 예상 비용 미정)
        assertThat(status.amountSteps()).extracting(BudgetStatusResponse.AmountStepStatus::code)
                .containsExactly("VENUE_CONTRACT", "SDM_CONTRACT");
        BudgetStatusResponse.AmountStepStatus venueStatus = status.amountSteps().get(0);
        assertThat(venueStatus.stepId()).isEqualTo(venue);
        assertThat(venueStatus.completed()).isTrue();
        assertThat(venueStatus.countedAs()).isEqualTo("SPENT");
        assertThat(venueStatus.actualAmount()).isEqualByComparingTo("25000000");
        assertThat(venueStatus.estimated().min()).isEqualByComparingTo("20000000");
        assertThat(venueStatus.estimated().max()).isEqualByComparingTo("30000000");
        BudgetStatusResponse.AmountStepStatus sdmStatus = status.amountSteps().get(1);
        assertThat(sdmStatus.countedAs()).isEqualTo("REMAINING_ESTIMATE");
        assertThat(sdmStatus.estimated().min()).isNull();
        assertThat(sdmStatus.estimated().max()).isNull();
    }

    @Test
    void 스드메_예상_비용이_있으면_남은_예산과_비교해_판정한다() {
        // 스드메 계산은 아직 미확정이라 단계 예상 비용을 테스트용 예시 값으로 직접 넣는다
        UUID sdm = stepOf("SDM_CONTRACT");
        jdbc.update("update plan_steps set estimated_min = 3000000, estimated_max = 6000000 where id = ?", sdm);
        UUID venue = stepOf("VENUE_CONTRACT");
        roadmapService.recordContractAmount(GROOM, venue, 25_000_000L);
        roadmapService.updateStepCompletion(GROOM, venue, true);
        flushAndClear();

        BudgetStatusResponse status = roadmapService.getBudgetStatus(GROOM);

        // 남은 예산 500만, 남은 예상 300만~600만 → 빠듯
        assertThat(status.remainingEstimate().min()).isEqualByComparingTo("3000000");
        assertThat(status.remainingEstimate().max()).isEqualByComparingTo("6000000");
        assertThat(status.verdict()).isEqualTo(Verdict.TIGHT);
    }

    @Test
    void 예식장_스드메를_모두_완료하면_판정_대신_완료_안내를_준다() {
        UUID venue = stepOf("VENUE_CONTRACT");
        UUID sdm = stepOf("SDM_CONTRACT");
        roadmapService.recordContractAmount(GROOM, venue, 25_000_000L);
        roadmapService.updateStepCompletion(GROOM, venue, true);
        roadmapService.recordContractAmount(GROOM, sdm, 7_000_000L);
        roadmapService.updateStepCompletion(GROOM, sdm, true);
        flushAndClear();

        BudgetStatusResponse status = roadmapService.getBudgetStatus(GROOM);

        assertThat(status.amountStepsCompleted()).isTrue();
        assertThat(status.allStepsCompleted()).isFalse();
        assertThat(status.spentAmount()).isEqualByComparingTo("32000000");
        assertThat(status.overBudgetAmount()).isEqualByComparingTo("2000000");
        assertThat(status.verdictSkipReason()).isEqualTo(SkipReason.AMOUNT_STEPS_COMPLETED);
    }

    @Test
    void 계획에_스드메_단계가_빠졌으면_나머지를_모두_완료해도_완료로_보지_않는다() {
        UUID venue = stepOf("VENUE_CONTRACT");
        UUID sdm = stepOf("SDM_CONTRACT");
        roadmapService.recordContractAmount(GROOM, venue, 25_000_000L);
        for (String code : List.of("BUDGET_AGREEMENT", "VENUE_CONTRACT", "SHOOTING_TRAVEL", "INVITATIONS_GUESTS", "FINAL_CHECK")) {
            roadmapService.updateStepCompletion(GROOM, stepOf(code), true);
        }
        flushAndClear();
        // 단계 데이터가 빠진 경우를 만든다
        jdbc.update("delete from plan_todos where plan_step_id = ?", sdm);
        jdbc.update("delete from plan_steps where id = ?", sdm);

        BudgetStatusResponse status = roadmapService.getBudgetStatus(GROOM);

        assertThat(status.amountSteps()).extracting(BudgetStatusResponse.AmountStepStatus::code)
                .containsExactly("VENUE_CONTRACT");
        assertThat(status.amountStepsCompleted()).isFalse();
        assertThat(status.allStepsCompleted()).isFalse();
        assertThat(status.spentAmount()).isEqualByComparingTo("25000000");
        assertThat(status.remainingBudget()).isEqualByComparingTo("5000000");
        assertThat(status.remainingEstimate()).isNull();
        assertThat(status.verdict()).isNull();
        assertThat(status.verdictSkipReason()).isEqualTo(SkipReason.AMOUNT_STEPS_MISSING);
    }

    @Test
    void 예산이_없으면_지금까지_쓴_금액만_준다() {
        planService.overwriteMine(GROOM, request(null));
        UUID venue = stepOf("VENUE_CONTRACT");
        roadmapService.recordContractAmount(GROOM, venue, 25_000_000L);
        roadmapService.updateStepCompletion(GROOM, venue, true);
        flushAndClear();

        BudgetStatusResponse status = roadmapService.getBudgetStatus(GROOM);

        assertThat(status.budget()).isNull();
        assertThat(status.spentAmount()).isEqualByComparingTo("25000000");
        assertThat(status.remainingBudget()).isNull();
        assertThat(status.verdictSkipReason()).isEqualTo(SkipReason.NO_BUDGET);
    }

    private void joinBride() {
        jdbc.update("insert into plan_members (plan_id, user_id, role) values (?, ?, 'PARTNER')", plan.planId(), BRIDE);
    }

    private UUID stepOf(String code) {
        return plan.steps().stream().filter(s -> s.code().equals(code)).findFirst().orElseThrow().stepId();
    }

    private UUID todoOf(String stepCode) {
        return jdbc.queryForObject(
                "select id from plan_todos where plan_step_id = ? order by title_snapshot limit 1",
                UUID.class, stepOf(stepCode));
    }

    private Map<String, Object> todoRow(UUID id) {
        return jdbc.queryForMap("select * from plan_todos where id = ?", id);
    }

    private Map<String, Object> stepRow(UUID id) {
        return jdbc.queryForMap("select * from plan_steps where id = ?", id);
    }

    private void hallEstimateReturns(String min, String max) {
        when(estimateService.calculateHallCostEstimate(any())).thenReturn(Optional.of(new HallCostEstimate(
                new BigDecimal(min), new BigDecimal(max), "HALL_DATA", null, null, null, 12)));
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    private static PlanRequest request(Long budget) {
        return new PlanRequest(
                "PLAN_TEST_REGION", "FROM_150_TO_200", WeddingDateType.EXACT,
                LocalDate.of(2027, 10, 9), null, null,
                budget, List.of("BRIGHT"), null);
    }
}
