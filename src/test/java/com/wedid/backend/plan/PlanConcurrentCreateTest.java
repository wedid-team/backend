package com.wedid.backend.plan;

import com.wedid.backend.estimate.dto.EstimateRequest.WeddingDateType;
import com.wedid.backend.estimate.service.EstimateService;
import com.wedid.backend.plan.dto.PlanRequest;
import com.wedid.backend.plan.dto.PlanResponse;
import com.wedid.backend.plan.service.PlanAlreadyExistsException;
import com.wedid.backend.plan.service.PlanEstimateRecorder;
import com.wedid.backend.plan.service.PlanPolicyResolver;
import com.wedid.backend.plan.service.PlanService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 같은 회원의 계획 저장 요청이 동시에 들어와 사전 중복 확인(existsByUserId)을 둘 다 통과한 경우,
 * DB의 UNIQUE 제약(uq_plans_active_owner)이 두 번째를 막고 409(PlanAlreadyExistsException)로 바뀌는지 확인한다.
 *
 * <p>두 트랜잭션이 실제로 커밋돼야 하므로 테스트 트랜잭션(롤백)을 쓰지 않고, 데이터를 직접 넣고 지운다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({PlanService.class, PlanPolicyResolver.class, PlanEstimateRecorder.class,
        PlanServiceIntegrationTest.TestConfig.class})
class PlanConcurrentCreateTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
    private static final short REGION_ID = 32002;

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
                + "values (?, 'PLAN_CONCURRENCY_REGION', '계획 동시성 테스트 권역', '[]', ?)", REGION_ID, REGION_ID);
        jdbc.update("insert into users (id, display_name) values (?, '동시 저장')", USER_ID);
        when(estimateService.calculateHallCostEstimate(any())).thenReturn(Optional.empty());
        executor = Executors.newFixedThreadPool(2);
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
        cleanUp();
    }

    @Test
    void 사전_확인을_통과한_동시_저장도_UNIQUE_제약에_걸려_409가_된다() throws Exception {
        CountDownLatch firstInserted = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        // 1. 첫 요청: 계획 행을 INSERT한 뒤 커밋하지 않고 멈춘다
        Future<PlanResponse> first = executor.submit(() -> transaction.execute(status -> {
            PlanResponse response = planService.create(USER_ID, request());
            firstInserted.countDown();
            await(releaseFirst);
            return response;
        }));
        assertThat(firstInserted.await(10, TimeUnit.SECONDS)).isTrue();

        // 2. 두 번째 요청: 첫 요청이 커밋 전이라 사전 확인은 통과하고, INSERT가 UNIQUE 인덱스 잠금을 기다린다
        Future<PlanResponse> second = executor.submit(() -> planService.create(USER_ID, request()));
        waitUntilSomeoneWaitsForLock();

        // 3. 첫 요청을 커밋하면 두 번째는 UNIQUE 위반 → PlanAlreadyExistsException
        releaseFirst.countDown();

        PlanResponse saved = first.get(10, TimeUnit.SECONDS);
        assertThatThrownBy(() -> second.get(10, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .cause()
                .isInstanceOf(PlanAlreadyExistsException.class)
                .hasMessage("이미 저장된 계획이 있어요.");

        assertThat(jdbc.queryForObject(
                "select count(*) from plans where owner_user_id = ?", Integer.class, USER_ID)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "select plan_id from plan_members where user_id = ?", UUID.class, USER_ID)).isEqualTo(saved.planId());
        assertThat(jdbc.queryForObject(
                "select count(*) from plan_steps where plan_id = ?", Integer.class, saved.planId())).isEqualTo(6);
    }

    private void waitUntilSomeoneWaitsForLock() throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            Integer waiting = jdbc.queryForObject(
                    "select count(*) from pg_stat_activity "
                            + "where datname = current_database() and wait_event_type = 'Lock' "
                            + "and query ilike 'insert into public.plans%'",
                    Integer.class);
            if (waiting != null && waiting > 0) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("두 번째 저장 요청이 잠금 대기에 들어가지 않았습니다.");
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(20, TimeUnit.SECONDS)) {
                throw new IllegalStateException("첫 요청 대기 시간 초과");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private void cleanUp() {
        String plansOfUser = "select id from plans where owner_user_id = ?";
        jdbc.update("update plans set current_estimate_id = null where owner_user_id = ?", USER_ID);
        jdbc.update("delete from plan_todos where plan_step_id in "
                + "(select id from plan_steps where plan_id in (" + plansOfUser + "))", USER_ID);
        jdbc.update("delete from plan_steps where plan_id in (" + plansOfUser + ")", USER_ID);
        jdbc.update("delete from estimate_runs where plan_id in (" + plansOfUser + ")", USER_ID);
        jdbc.update("delete from plan_members where user_id = ?", USER_ID);
        jdbc.update("delete from plans where owner_user_id = ?", USER_ID);
        jdbc.update("delete from users where id = ?", USER_ID);
        jdbc.update("delete from regions where id = ?", REGION_ID);
    }

    private static PlanRequest request() {
        return new PlanRequest(
                "PLAN_CONCURRENCY_REGION", "FROM_150_TO_200", WeddingDateType.EXACT,
                LocalDate.of(2027, 10, 9), null, null, null, List.of(), null);
    }
}
