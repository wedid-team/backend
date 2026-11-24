package com.wedid.backend.invitation;

import com.wedid.backend.estimate.dto.EstimateRequest.WeddingDateType;
import com.wedid.backend.estimate.service.EstimateService;
import com.wedid.backend.invitation.dto.InvitationAcceptResponse;
import com.wedid.backend.invitation.service.InvitationConflictException;
import com.wedid.backend.invitation.service.InvitationExpiredException;
import com.wedid.backend.invitation.service.InvitationService;
import com.wedid.backend.plan.dto.PlanRequest;
import com.wedid.backend.plan.service.PlanEstimateRecorder;
import com.wedid.backend.plan.service.PlanPolicyResolver;
import com.wedid.backend.plan.service.PlanService;
import com.wedid.backend.sdm.repository.SdmBasePriceRepository;
import com.wedid.backend.sdm.service.SdmEstimateCalculator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 합류 요청이 동시에 들어와도 계획당 최대 2명 · 1인 1계획이 지켜지고, 실패한 요청은 정의된 예외(409 · 410)로 끝나는지 확인한다.
 * <ul>
 *   <li>같은 링크로 두 사람이 동시에 합류: 계획 행 잠금으로 순서가 정해져 한 명만 PARTNER가 되고 나머지는 409</li>
 *   <li>두 사람이 서로의 링크로 동시에 합류: 계획 ID 순서로 잠그므로 교착(deadlock) 없이 한 쪽만 성공</li>
 * </ul>
 *
 * <p>트랜잭션이 실제로 커밋돼야 하므로 테스트 트랜잭션(롤백)을 쓰지 않고, 데이터를 직접 넣고 지운다
 * (PlanConcurrentCreateTest와 같은 방식).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({InvitationService.class, PlanService.class, PlanPolicyResolver.class, PlanEstimateRecorder.class,
        SdmEstimateCalculator.class, SdmBasePriceRepository.class,
        InvitationConcurrentAcceptTest.TestConfig.class})
class InvitationConcurrentAcceptTest {

    private static final UUID OWNER_A = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
    private static final UUID OWNER_B = UUID.fromString("00000000-0000-0000-0000-0000000000c2");
    private static final UUID JOINER_1 = UUID.fromString("00000000-0000-0000-0000-0000000000c3");
    private static final UUID JOINER_2 = UUID.fromString("00000000-0000-0000-0000-0000000000c4");
    private static final List<UUID> USERS = List.of(OWNER_A, OWNER_B, JOINER_1, JOINER_2);
    private static final short REGION_ID = 32003;

    @TestConfiguration
    static class TestConfig {
        @Bean
        Clock clock() {
            return Clock.systemUTC();
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
    private JdbcTemplate jdbc;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @MockitoBean
    private EstimateService estimateService;

    private ExecutorService executor;

    @BeforeEach
    void setUp() {
        cleanUp();
        jdbc.update("insert into regions (id, code, display_name, source_names, sort_order) "
                + "values (?, 'INVITATION_CONCURRENCY_REGION', '초대 동시성 테스트 권역', '[]', ?)", REGION_ID, REGION_ID);
        for (UUID user : USERS) {
            jdbc.update("insert into users (id, display_name) values (?, '동시 합류')", user);
        }
        when(estimateService.calculateHallCostEstimate(any())).thenReturn(Optional.empty());
        executor = Executors.newFixedThreadPool(3);
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
        cleanUp();
    }

    @Test
    void 같은_링크로_두_사람이_동시에_합류하면_한_명만_성공하고_나머지는_409다() throws Exception {
        UUID planId = planService.create(OWNER_A, request()).planId();
        String token = invitationService.create(OWNER_A).token();

        CountDownLatch firstJoined = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        // 1. 첫 요청: 계획을 잠그고 PARTNER로 들어간 뒤 커밋하지 않고 멈춘다
        Future<InvitationAcceptResponse> first = executor.submit(() -> transaction.execute(status -> {
            InvitationAcceptResponse response = invitationService.accept(token, JOINER_1, false);
            firstJoined.countDown();
            await(releaseFirst);
            return response;
        }));
        assertThat(firstJoined.await(10, TimeUnit.SECONDS)).isTrue();

        // 2. 두 번째 요청: 같은 계획 행 잠금을 기다린다
        Future<InvitationAcceptResponse> second = executor.submit(() -> invitationService.accept(token, JOINER_2, false));
        waitUntilPlanLockWaiters(1);

        // 3. 첫 요청이 커밋되면 두 번째는 잠금을 얻고 "이미 짝꿍과 연결된 계획"으로 끝난다
        releaseFirst.countDown();

        assertThat(first.get(10, TimeUnit.SECONDS).planId()).isEqualTo(planId);
        Throwable failure = failureOf(second);
        assertThat(failure).isInstanceOf(InvitationConflictException.class);
        assertThat(((InvitationConflictException) failure).getCode()).isEqualTo("PLAN_ALREADY_CONNECTED");

        assertThat(jdbc.queryForList("select user_id from plan_members where plan_id = ? and role = 'PARTNER'",
                UUID.class, planId)).containsExactly(JOINER_1);
        assertThat(jdbc.queryForObject("select count(*) from plan_members where plan_id = ?", Integer.class, planId))
                .isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from plan_members where user_id = ?", Integer.class, JOINER_2))
                .isZero();
        assertThat(jdbc.queryForObject("select accepted_by from plan_invitations where plan_id = ?", UUID.class, planId))
                .isEqualTo(JOINER_1);
    }

    @Test
    void 두_사람이_서로의_링크로_동시에_합류해도_교착_없이_한_쪽만_성공한다() throws Exception {
        UUID planA = planService.create(OWNER_A, request()).planId();
        UUID planB = planService.create(OWNER_B, request()).planId();
        String tokenOfA = invitationService.create(OWNER_A).token();
        String tokenOfB = invitationService.create(OWNER_B).token();

        // 1. 다른 트랜잭션이 두 계획을 모두 잠가 두 합류 요청을 같은 출발선에 세운다.
        //    잠금을 풀면 두 요청이 동시에 잠금을 다툰다. 계획 ID 순서로 잠그지 않으면 이때
        //    "A: 계획 B 잠금 → 계획 A 대기 / B: 계획 A 잠금 → 계획 B 대기"로 교착된다.
        CountDownLatch locksHeld = new CountDownLatch(1);
        CountDownLatch releaseLocks = new CountDownLatch(1);
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        Future<?> holder = executor.submit(() -> transaction.executeWithoutResult(status -> {
            jdbc.queryForList("select id from plans where id in (?, ?) for update", UUID.class, planA, planB);
            locksHeld.countDown();
            await(releaseLocks);
        }));
        assertThat(locksHeld.await(10, TimeUnit.SECONDS)).isTrue();

        // 2. A는 B의 링크로, B는 A의 링크로 합류한다(둘 다 내 계획 교체에 동의)
        Future<InvitationAcceptResponse> aJoinsB = executor.submit(() -> invitationService.accept(tokenOfB, OWNER_A, true));
        Future<InvitationAcceptResponse> bJoinsA = executor.submit(() -> invitationService.accept(tokenOfA, OWNER_B, true));
        waitUntilPlanLockWaiters(2);

        releaseLocks.countDown();
        holder.get(10, TimeUnit.SECONDS);

        // 3. 교착이면 PostgreSQL이 한 쪽을 잠금 오류로 끊는다. 정의된 초대 예외 외에는 실패로 본다
        List<Future<InvitationAcceptResponse>> requests = List.of(aJoinsB, bJoinsA);
        List<InvitationAcceptResponse> succeeded = new ArrayList<>();
        List<Throwable> failed = new ArrayList<>();
        for (Future<InvitationAcceptResponse> request : requests) {
            try {
                succeeded.add(request.get(15, TimeUnit.SECONDS));
            } catch (ExecutionException e) {
                failed.add(e.getCause());
            }
        }
        assertThat(succeeded).hasSize(1);
        assertThat(failed).hasSize(1);
        // 진 쪽이 합류하려던 계획은 이긴 쪽이 교체(REPLACED)했으므로 "만료된 링크"가 된다
        assertThat(failed.getFirst()).isInstanceOfAny(InvitationExpiredException.class, InvitationConflictException.class);

        // 4. 최종 상태: 활성 계획 하나에 두 사람, 다른 계획은 멤버 없이 REPLACED로 보관
        InvitationAcceptResponse winner = succeeded.getFirst();
        UUID survivor = winner.planId();
        UUID replaced = winner.replacedPlanId();
        assertThat(List.of(survivor, replaced)).containsExactlyInAnyOrder(planA, planB);
        assertThat(planStatus(survivor)).isEqualTo("ACTIVE");
        assertThat(planStatus(replaced)).isEqualTo("REPLACED");
        assertThat(jdbc.queryForList("select user_id from plan_members where plan_id = ?", UUID.class, survivor))
                .containsExactlyInAnyOrder(OWNER_A, OWNER_B);
        assertThat(jdbc.queryForObject("select count(*) from plan_members where plan_id = ?", Integer.class, replaced))
                .isZero();
    }

    private String planStatus(UUID planId) {
        return jdbc.queryForObject("select status from plans where id = ?", String.class, planId);
    }

    /** 계획 행 잠금(SELECT … FOR UPDATE)을 기다리는 세션이 count개 이상이 될 때까지 기다린다. */
    private void waitUntilPlanLockWaiters(int count) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            Integer waiting = jdbc.queryForObject(
                    "select count(*) from pg_stat_activity "
                            + "where datname = current_database() and wait_event_type = 'Lock' "
                            + "and query ilike '%from public.plans%' and query ilike '%for %update%'",
                    Integer.class);
            if (waiting != null && waiting >= count) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("합류 요청이 계획 잠금 대기에 들어가지 않았습니다.");
    }

    private static Throwable failureOf(Future<?> future) throws Exception {
        try {
            future.get(10, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            return e.getCause();
        }
        throw new AssertionError("요청이 실패하지 않았습니다.");
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(20, TimeUnit.SECONDS)) {
                throw new IllegalStateException("대기 시간 초과");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    /** 테스트 회원 · 권역과 그들이 만든 계획 관련 행을 모두 지운다(FK 순서대로). */
    private void cleanUp() {
        Object[] users = USERS.toArray();
        String in = "(?, ?, ?, ?)";
        String plansOfUsers = "select id from plans where owner_user_id in " + in;
        jdbc.update("delete from plan_invitations where plan_id in (" + plansOfUsers + ") "
                + "or invited_by in " + in + " or accepted_by in " + in, concat(users, users, users));
        jdbc.update("update plans set current_estimate_id = null where owner_user_id in " + in, users);
        jdbc.update("delete from plan_todos where plan_step_id in "
                + "(select id from plan_steps where plan_id in (" + plansOfUsers + "))", users);
        jdbc.update("delete from plan_steps where plan_id in (" + plansOfUsers + ")", users);
        jdbc.update("delete from estimate_runs where plan_id in (" + plansOfUsers + ")", users);
        jdbc.update("delete from plan_members where user_id in " + in
                + " or plan_id in (" + plansOfUsers + ")", concat(users, users));
        jdbc.update("delete from plans where owner_user_id in " + in, users);
        jdbc.update("delete from users where id in " + in, users);
        jdbc.update("delete from regions where id = ?", REGION_ID);
    }

    private static Object[] concat(Object[]... arrays) {
        List<Object> all = new ArrayList<>();
        for (Object[] array : arrays) {
            all.addAll(List.of(array));
        }
        return all.toArray();
    }

    private static PlanRequest request() {
        return new PlanRequest(
                "INVITATION_CONCURRENCY_REGION", "FROM_150_TO_200", WeddingDateType.EXACT,
                LocalDate.of(2027, 10, 9), null, null, null, List.of(), null);
    }
}
