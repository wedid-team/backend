package com.wedid.backend.invitation;

import com.wedid.backend.estimate.dto.EstimateRequest.WeddingDateType;
import com.wedid.backend.estimate.dto.HallCostEstimate;
import com.wedid.backend.estimate.service.EstimateService;
import com.wedid.backend.invitation.dto.InvitationAcceptResponse;
import com.wedid.backend.invitation.dto.InvitationCreateResponse;
import com.wedid.backend.invitation.dto.InvitationPreviewResponse;
import com.wedid.backend.invitation.dto.InvitationPreviewResponse.Status;
import com.wedid.backend.invitation.dto.InvitationPreviewResponse.ViewerPlan;
import com.wedid.backend.invitation.service.InvitationConflictException;
import com.wedid.backend.invitation.service.InvitationExpiredException;
import com.wedid.backend.invitation.service.InvitationService;
import com.wedid.backend.plan.dto.PlanRequest;
import com.wedid.backend.plan.dto.PlanResponse;
import com.wedid.backend.plan.service.PlanAlreadyExistsException;
import com.wedid.backend.plan.service.PlanEstimateRecorder;
import com.wedid.backend.plan.service.PlanNotFoundException;
import com.wedid.backend.plan.service.PlanPolicyResolver;
import com.wedid.backend.plan.service.PlanService;
import com.wedid.backend.roadmap.dto.RoadmapResponse;
import com.wedid.backend.roadmap.dto.TodoResponse;
import com.wedid.backend.roadmap.dto.TodoUpdateRequest;
import com.wedid.backend.roadmap.service.RoadmapItemNotFoundException;
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
 * 로컬 PostgreSQL(docker compose)에 대해 짝꿍 초대 발급 · 확인 · 합류와
 * 합류 후 두 사람이 같은 로드맵을 보고 고치는지 검증한다. 각 테스트는 끝나면 롤백된다.
 * 시계는 테스트마다 옮길 수 있게 {@link MutableClock}을 쓴다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({InvitationService.class, RoadmapService.class, PlanService.class, PlanPolicyResolver.class,
        PlanEstimateRecorder.class, SdmEstimateCalculator.class, SdmBasePriceRepository.class,
        InvitationServiceIntegrationTest.TestConfig.class})
@Sql({"/sql/draft-policy-only.sql", "/sql/plan-fixture.sql"})
class InvitationServiceIntegrationTest {

    static final UUID GROOM = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    static final UUID BRIDE = UUID.fromString("00000000-0000-0000-0000-0000000000a2");
    static final UUID STRANGER = UUID.fromString("00000000-0000-0000-0000-0000000000a3");

    static final Instant NOW = Instant.parse("2026-11-24T03:00:00Z");

    static final class MutableClock extends Clock {
        private Instant instant = NOW;

        void set(Instant instant) {
            this.instant = instant;
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return Clock.fixed(instant, zone);
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }

    @TestConfiguration
    static class TestConfig {
        @Bean
        MutableClock clock() {
            return new MutableClock();
        }

        @Bean
        JsonMapper jsonMapper() {
            return JsonMapper.builder().build();
        }
    }

    @Autowired
    private InvitationService invitationService;

    @Autowired
    private PlanService planService;

    @Autowired
    private RoadmapService roadmapService;

    @Autowired
    private MutableClock clock;

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbc;

    @MockitoBean
    private EstimateService estimateService;

    private PlanResponse groomPlan;

    @BeforeEach
    void setUp() {
        clock.set(NOW);
        when(estimateService.calculateHallCostEstimate(any())).thenReturn(Optional.of(new HallCostEstimate(
                new BigDecimal("20000000"), new BigDecimal("30000000"), "HALL_DATA", null, null, null, 12)));
        groomPlan = planService.create(GROOM, request());
        flushAndClear();
    }

    // ---- 발급 ----

    @Test
    void 초대_링크는_7일_동안_쓸_수_있고_토큰_평문은_저장하지_않는다() {
        InvitationCreateResponse created = invitationService.create(GROOM);
        flushAndClear();

        assertThat(created.token()).hasSizeGreaterThanOrEqualTo(43);
        assertThat(created.expiresAt()).isEqualTo(OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC).plusDays(7));
        Map<String, Object> row = jdbc.queryForMap("select * from plan_invitations where plan_id = ?", groomPlan.planId());
        assertThat(row.get("token_hash")).isNotEqualTo(created.token());
        assertThat(row).containsEntry("invited_by", GROOM).containsEntry("accepted_by", null);
    }

    @Test
    void 새로_발급해도_이전_링크는_7일_안이면_계속_쓸_수_있다() {
        String first = invitationService.create(GROOM).token();
        clock.set(NOW.plusSeconds(3 * 24 * 3600));
        String second = invitationService.create(GROOM).token();
        flushAndClear();

        assertThat(invitationService.preview(first, null).status()).isEqualTo(Status.VALID);
        assertThat(invitationService.preview(second, null).status()).isEqualTo(Status.VALID);
        assertThat(jdbc.queryForObject(
                "select count(*) from plan_invitations where plan_id = ? and revoked_at is null",
                Integer.class, groomPlan.planId())).isEqualTo(2);

        // 링크마다 발급 시각부터 7일: 첫 링크만 먼저 만료된다
        clock.set(NOW.plusSeconds(7 * 24 * 3600));
        assertThat(invitationService.preview(first, null).status()).isEqualTo(Status.EXPIRED);
        assertThat(invitationService.preview(second, null).status()).isEqualTo(Status.VALID);
    }

    @Test
    void 이전_링크로도_합류할_수_있다() {
        String first = invitationService.create(GROOM).token();
        invitationService.create(GROOM);
        flushAndClear();

        assertThat(invitationService.accept(first, BRIDE, false).planId()).isEqualTo(groomPlan.planId());
    }

    @Test
    void 계획이_없으면_초대할_수_없다() {
        assertThatThrownBy(() -> invitationService.create(STRANGER)).isInstanceOf(PlanNotFoundException.class);
    }

    @Test
    void 짝꿍이_이미_있으면_초대할_수_없다() {
        invitationService.accept(invitationService.create(GROOM).token(), BRIDE, false);
        flushAndClear();

        assertThatThrownBy(() -> invitationService.create(GROOM))
                .isInstanceOf(InvitationConflictException.class)
                .hasMessage("이미 짝꿍과 연결된 계획이에요.");
        assertThatThrownBy(() -> invitationService.create(BRIDE))
                .isInstanceOf(InvitationConflictException.class)
                .hasMessage("이미 짝꿍과 연결된 계획이에요.");
    }

    // ---- 확인 ----

    @Test
    void 로그인하지_않아도_링크_상태와_초대한_사람을_볼_수_있다() {
        String token = invitationService.create(GROOM).token();
        flushAndClear();

        InvitationPreviewResponse preview = invitationService.preview(token, null);

        assertThat(preview.status()).isEqualTo(Status.VALID);
        assertThat(preview.inviterName()).isEqualTo("신랑");
        assertThat(preview.viewerPlan()).isNull();
    }

    @Test
    void 로그인했으면_내_계획_상태를_함께_준다() {
        String token = invitationService.create(GROOM).token();
        flushAndClear();

        assertThat(invitationService.preview(token, BRIDE).viewerPlan()).isEqualTo(ViewerPlan.NONE);
        planService.create(BRIDE, request());
        flushAndClear();
        assertThat(invitationService.preview(token, BRIDE).viewerPlan()).isEqualTo(ViewerPlan.HAS_PLAN);
    }

    @Test
    void 자기_링크를_열면_이미_멤버로_알린다() {
        String token = invitationService.create(GROOM).token();
        flushAndClear();

        InvitationPreviewResponse preview = invitationService.preview(token, GROOM);

        assertThat(preview.status()).isEqualTo(Status.ALREADY_MEMBER);
        assertThat(preview.viewerPlan()).isNull();
        assertThatThrownBy(() -> invitationService.accept(token, GROOM, false))
                .isInstanceOf(InvitationConflictException.class)
                .hasMessage("이미 함께하고 있는 계획이에요.");
    }

    @Test
    void 없는_토큰은_만료로_알린다() {
        assertThatThrownBy(() -> invitationService.preview("no-such-token", null))
                .isInstanceOf(InvitationExpiredException.class)
                .hasMessage("초대 링크가 만료됐어요.");
        assertThatThrownBy(() -> invitationService.accept("no-such-token", BRIDE, false))
                .isInstanceOf(InvitationExpiredException.class);
    }

    // ---- 만료 ----

    @Test
    void 칠일이_지나면_만료되어_합류할_수_없다() {
        String token = invitationService.create(GROOM).token();
        flushAndClear();

        clock.set(NOW.plusSeconds(7 * 24 * 3600 - 1));
        assertThat(invitationService.preview(token, null).status()).isEqualTo(Status.VALID);

        clock.set(NOW.plusSeconds(7 * 24 * 3600));
        assertThat(invitationService.preview(token, null).status()).isEqualTo(Status.EXPIRED);
        assertThat(invitationService.preview(token, null).inviterName()).isNull();
        assertThatThrownBy(() -> invitationService.accept(token, BRIDE, false))
                .isInstanceOf(InvitationExpiredException.class);
        assertThat(memberCount(groomPlan.planId())).isEqualTo(1);
    }

    // ---- 합류 ----

    @Test
    void 합류하면_짝꿍이_되고_링크는_더_쓸_수_없다() {
        String token = invitationService.create(GROOM).token();
        flushAndClear();

        InvitationAcceptResponse accepted = invitationService.accept(token, BRIDE, false);
        flushAndClear();

        assertThat(accepted.planId()).isEqualTo(groomPlan.planId());
        assertThat(accepted.role()).isEqualTo("PARTNER");
        assertThat(accepted.joinedAt()).isEqualTo(OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC));
        assertThat(accepted.replacedPlanId()).isNull();
        assertThat(jdbc.queryForObject("select role from plan_members where user_id = ?", String.class, BRIDE))
                .isEqualTo("PARTNER");
        Map<String, Object> row = jdbc.queryForMap("select * from plan_invitations where plan_id = ?", groomPlan.planId());
        assertThat(row).containsEntry("accepted_by", BRIDE);

        // 같은 사람이 다시 눌러도 중복 합류하지 않는다
        assertThat(invitationService.preview(token, BRIDE).status()).isEqualTo(Status.ALREADY_MEMBER);
        assertThatThrownBy(() -> invitationService.accept(token, BRIDE, false))
                .isInstanceOf(InvitationConflictException.class)
                .hasMessage("이미 함께하고 있는 계획이에요.");
        // 세 번째 사람은 합류할 수 없다(최대 2명)
        assertThat(invitationService.preview(token, STRANGER).status()).isEqualTo(Status.ALREADY_CONNECTED);
        assertThatThrownBy(() -> invitationService.accept(token, STRANGER, false))
                .isInstanceOf(InvitationConflictException.class)
                .hasMessage("이미 짝꿍과 연결된 계획이에요.");
        assertThat(memberCount(groomPlan.planId())).isEqualTo(2);
    }

    @Test
    void 합류하면_그_계획의_남은_링크는_모두_폐기된다() {
        String first = invitationService.create(GROOM).token();
        String second = invitationService.create(GROOM).token();
        String third = invitationService.create(GROOM).token();
        flushAndClear();

        invitationService.accept(second, BRIDE, false);
        flushAndClear();

        assertThat(jdbc.queryForObject("""
                select count(*) from plan_invitations
                where plan_id = ? and accepted_at is null and revoked_at is null""",
                Integer.class, groomPlan.planId())).isZero();
        assertThat(jdbc.queryForObject(
                "select count(*) from plan_invitations where plan_id = ? and revoked_at is not null",
                Integer.class, groomPlan.planId())).isEqualTo(2);
        // 남은 링크로는 세 번째 사람이 합류할 수 없다
        for (String token : List.of(first, third)) {
            assertThatThrownBy(() -> invitationService.accept(token, STRANGER, false))
                    .isInstanceOf(InvitationConflictException.class)
                    .hasMessage("이미 짝꿍과 연결된 계획이에요.");
        }
        assertThat(memberCount(groomPlan.planId())).isEqualTo(2);
    }

    @Test
    void 내_계획이_있으면_교체에_동의해야_합류한다() {
        PlanResponse bridePlan = planService.create(BRIDE, request());
        String token = invitationService.create(GROOM).token();
        flushAndClear();

        assertThatThrownBy(() -> invitationService.accept(token, BRIDE, false))
                .isInstanceOf(InvitationConflictException.class)
                .hasMessage("합류하면 내 계획은 사라져요.");
        flushAndClear();
        assertThat(planStatus(bridePlan.planId())).isEqualTo("ACTIVE");

        InvitationAcceptResponse accepted = invitationService.accept(token, BRIDE, true);
        flushAndClear();

        assertThat(accepted.replacedPlanId()).isEqualTo(bridePlan.planId());
        assertThat(planStatus(bridePlan.planId())).isEqualTo("REPLACED");
        assertThat(memberCount(bridePlan.planId())).isZero();
        // 기록은 지우지 않는다
        assertThat(jdbc.queryForObject("select count(*) from plan_steps where plan_id = ?", Integer.class,
                bridePlan.planId())).isEqualTo(6);
        assertThat(planService.getMine(BRIDE).planId()).isEqualTo(groomPlan.planId());
    }

    @Test
    void 교체된_계획의_링크는_쓸_수_없다() {
        planService.create(BRIDE, request());
        String brideToken = invitationService.create(BRIDE).token();
        String groomToken = invitationService.create(GROOM).token();
        flushAndClear();

        invitationService.accept(groomToken, BRIDE, true);
        flushAndClear();

        assertThat(invitationService.preview(brideToken, STRANGER).status()).isEqualTo(Status.EXPIRED);
        assertThatThrownBy(() -> invitationService.accept(brideToken, STRANGER, false))
                .isInstanceOf(InvitationExpiredException.class);
    }

    @Test
    void 이미_다른_짝꿍과_연결된_사람은_합류할_수_없다() {
        // 다른 회원의 계획에 신부가 짝꿍으로 합류해 있다
        planService.create(STRANGER, request());
        invitationService.accept(invitationService.create(STRANGER).token(), BRIDE, false);
        String groomToken = invitationService.create(GROOM).token();
        flushAndClear();

        assertThat(invitationService.preview(groomToken, BRIDE).viewerPlan()).isEqualTo(ViewerPlan.HAS_PARTNER);
        assertThatThrownBy(() -> invitationService.accept(groomToken, BRIDE, true))
                .isInstanceOf(InvitationConflictException.class)
                .hasMessage("이미 짝꿍과 연결되어 있어요. 연결을 해제한 뒤 합류할 수 있어요.");
        // 짝꿍이 있는 계획의 주인도 마찬가지
        assertThatThrownBy(() -> invitationService.accept(groomToken, STRANGER, true))
                .isInstanceOf(InvitationConflictException.class)
                .hasMessage("이미 짝꿍과 연결되어 있어요. 연결을 해제한 뒤 합류할 수 있어요.");
        assertThat(memberCount(groomPlan.planId())).isEqualTo(1);
    }

    @Test
    void 짝꿍으로_합류한_사람은_새_계획을_만들_수_없다() {
        invitationService.accept(invitationService.create(GROOM).token(), BRIDE, false);
        flushAndClear();

        assertThatThrownBy(() -> planService.create(BRIDE, request())).isInstanceOf(PlanAlreadyExistsException.class);
    }

    // ---- 합류 후 공동 준비 ----

    @Test
    void 두_사람_모두_같은_로드맵을_보고_서로를_짝꿍으로_본다() {
        invitationService.accept(invitationService.create(GROOM).token(), BRIDE, false);
        flushAndClear();

        RoadmapResponse byGroom = roadmapService.getRoadmap(GROOM);
        RoadmapResponse byBride = roadmapService.getRoadmap(BRIDE);

        assertThat(byBride.planId()).isEqualTo(byGroom.planId()).isEqualTo(groomPlan.planId());
        assertThat(byBride.steps()).extracting(RoadmapResponse.Step::stepId)
                .containsExactlyElementsOf(byGroom.steps().stream().map(RoadmapResponse.Step::stepId).toList());
        assertThat(byGroom.partner().userId()).isEqualTo(BRIDE);
        assertThat(byGroom.partner().role()).isEqualTo("PARTNER");
        assertThat(byBride.me().role()).isEqualTo("PARTNER");
        assertThat(byBride.partner().userId()).isEqualTo(GROOM);
    }

    @Test
    void 합류한_짝꿍은_할_일을_고치고_담당을_맡길_수_있다() {
        UUID taskId = anyTodo(groomPlan.planId());
        assertThatThrownBy(() -> roadmapService.updateTodo(GROOM, taskId, new TodoUpdateRequest(null, "PARTNER")))
                .isInstanceOf(IllegalArgumentException.class);

        invitationService.accept(invitationService.create(GROOM).token(), BRIDE, false);
        flushAndClear();

        TodoResponse byBride = roadmapService.updateTodo(BRIDE, taskId, new TodoUpdateRequest(true, "PARTNER"));
        flushAndClear();
        assertThat(byBride.assignee()).isEqualTo("PARTNER");
        assertThat(byBride.assignedUserId()).isEqualTo(GROOM);
        assertThat(byBride.updatedBy()).isEqualTo(BRIDE);

        TodoResponse seenByGroom = roadmapService.getRoadmap(GROOM).steps().stream()
                .flatMap(step -> step.todos().stream())
                .filter(todo -> todo.taskId().equals(taskId))
                .findFirst().orElseThrow();
        assertThat(seenByGroom.assignee()).isEqualTo("ME");
        assertThat(seenByGroom.completed()).isTrue();
    }

    @Test
    void 계획_멤버가_아니면_할_일을_고칠_수_없다() {
        UUID taskId = anyTodo(groomPlan.planId());
        planService.create(STRANGER, request());
        flushAndClear();

        assertThatThrownBy(() -> roadmapService.updateTodo(STRANGER, taskId, new TodoUpdateRequest(true, null)))
                .isInstanceOf(RoadmapItemNotFoundException.class);
    }

    @Test
    void 교체된_내_예전_계획의_할_일은_더_고칠_수_없다() {
        PlanResponse bridePlan = planService.create(BRIDE, request());
        flushAndClear();
        UUID oldTaskId = anyTodo(bridePlan.planId());
        invitationService.accept(invitationService.create(GROOM).token(), BRIDE, true);
        flushAndClear();

        assertThatThrownBy(() -> roadmapService.updateTodo(BRIDE, oldTaskId, new TodoUpdateRequest(true, null)))
                .isInstanceOf(RoadmapItemNotFoundException.class);
    }

    private int memberCount(UUID planId) {
        return jdbc.queryForObject("select count(*) from plan_members where plan_id = ?", Integer.class, planId);
    }

    private String planStatus(UUID planId) {
        return jdbc.queryForObject("select status from plans where id = ?", String.class, planId);
    }

    private UUID anyTodo(UUID planId) {
        List<UUID> ids = jdbc.queryForList("""
                select t.id from plan_todos t join plan_steps s on s.id = t.plan_step_id
                where s.plan_id = ? order by t.title_snapshot""", UUID.class, planId);
        return ids.getFirst();
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    private static PlanRequest request() {
        return new PlanRequest(
                "PLAN_TEST_REGION", "FROM_150_TO_200", WeddingDateType.EXACT,
                LocalDate.of(2027, 10, 9), null, null,
                30_000_000L, List.of("BRIGHT"), null);
    }
}
