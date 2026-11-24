package com.wedid.backend.roadmap.service;

import com.wedid.backend.estimate.entity.EstimateRun;
import com.wedid.backend.estimate.repository.EstimateRunRepository;
import com.wedid.backend.plan.entity.Plan;
import com.wedid.backend.plan.entity.PlanMember;
import com.wedid.backend.plan.repository.PlanMemberRepository;
import com.wedid.backend.plan.repository.PlanRepository;
import com.wedid.backend.plan.service.PlanNotFoundException;
import com.wedid.backend.roadmap.dto.BudgetStatusResponse;
import com.wedid.backend.roadmap.dto.RoadmapStepResponse;
import com.wedid.backend.roadmap.dto.TodoResponse;
import com.wedid.backend.roadmap.dto.TodoUpdateRequest;
import com.wedid.backend.roadmap.entity.PlanStep;
import com.wedid.backend.roadmap.entity.PlanTodo;
import com.wedid.backend.roadmap.entity.PlanTodo.AssignmentKind;
import com.wedid.backend.roadmap.entity.RoadmapStepTemplate;
import com.wedid.backend.roadmap.repository.PlanStepRepository;
import com.wedid.backend.roadmap.repository.PlanTodoRepository;
import com.wedid.backend.roadmap.repository.RoadmapStepTemplateRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 로드맵 진행: 할 일 체크 · 담당, 실제 계약 금액, 단계 완료, 남은 예산 판정.
 * <ul>
 *   <li>계획을 만든 사람과 합류한 짝꿍 모두 같은 계획을 고친다. 내 계획에 없는 단계 · 할 일은 404.</li>
 *   <li>고칠 때는 계획 행을 잠가 저장한 순서대로 마지막 수정이 남게 한다(P-04). 충돌 안내는 없다.</li>
 *   <li>단계 · 할 일마다 마지막 수정자 · 수정 시각(updated_by · updated_at)을 남긴다.</li>
 * </ul>
 */
@Service
public class RoadmapService {

    private final PlanRepository planRepository;
    private final PlanMemberRepository planMemberRepository;
    private final PlanStepRepository planStepRepository;
    private final PlanTodoRepository planTodoRepository;
    private final RoadmapStepTemplateRepository stepTemplateRepository;
    private final EstimateRunRepository estimateRunRepository;
    private final Clock clock;

    public RoadmapService(
            PlanRepository planRepository,
            PlanMemberRepository planMemberRepository,
            PlanStepRepository planStepRepository,
            PlanTodoRepository planTodoRepository,
            RoadmapStepTemplateRepository stepTemplateRepository,
            EstimateRunRepository estimateRunRepository,
            Clock clock
    ) {
        this.planRepository = planRepository;
        this.planMemberRepository = planMemberRepository;
        this.planStepRepository = planStepRepository;
        this.planTodoRepository = planTodoRepository;
        this.stepTemplateRepository = stepTemplateRepository;
        this.estimateRunRepository = estimateRunRepository;
        this.clock = clock;
    }

    /** 할 일 완료 · 미완료, 담당 변경(P-07). 보낸 항목만 바꾼다. */
    @Transactional
    public TodoResponse updateTodo(UUID userId, UUID taskId, TodoUpdateRequest request) {
        Plan plan = myPlan(userId, true);
        PlanTodo todo = planTodoRepository.findByIdAndPlanId(taskId, plan.getId())
                .orElseThrow(RoadmapItemNotFoundException::todo);
        List<PlanMember> members = planMemberRepository.findByPlanId(plan.getId());
        OffsetDateTime now = OffsetDateTime.now(clock);

        if (request.assignee() != null) {
            assign(todo, request.assignee(), userId, members, now);
        }
        if (request.completed() != null) {
            todo.markCompleted(request.completed(), userId, now);
        }
        return toTodoResponse(todo, userId, members);
    }

    /**
     * 단계 완료 · 완료 취소. 완료를 취소해도 실제 계약 금액은 남기고,
     * 예상 비용은 계획의 현재 계산 결과로 맞춘다(다시 미완료 단계가 되므로).
     */
    @Transactional
    public RoadmapStepResponse updateStepCompletion(UUID userId, UUID stepId, boolean completed) {
        Plan plan = myPlan(userId, true);
        PlanStep step = myStep(plan, stepId);
        RoadmapStepTemplate template = template(step);
        OffsetDateTime now = OffsetDateTime.now(clock);

        if (completed) {
            step.complete(template, userId, now);
        } else {
            EstimateRun currentEstimate = Optional.ofNullable(plan.getCurrentEstimateId())
                    .flatMap(estimateRunRepository::findById)
                    .orElse(null);
            step.reopen(template, currentEstimate, userId, now);
        }
        return toStepResponse(step, template);
    }

    /** 예식장 · 스드메 실제 계약 금액 입력 · 수정(R-04). 단계 완료는 따로 한다. */
    @Transactional
    public RoadmapStepResponse recordContractAmount(UUID userId, UUID stepId, long actualAmount) {
        Plan plan = myPlan(userId, true);
        PlanStep step = myStep(plan, stepId);
        RoadmapStepTemplate template = template(step);

        step.recordActualAmount(template, BigDecimal.valueOf(actualAmount), userId, OffsetDateTime.now(clock));
        return toStepResponse(step, template);
    }

    /** 남은 예산 판정(R-03 · R-06 · R-09 · R-10). */
    @Transactional(readOnly = true)
    public BudgetStatusResponse getBudgetStatus(UUID userId) {
        Plan plan = myPlan(userId, false);
        Map<Short, RoadmapStepTemplate> templateById = stepTemplateRepository.findAll().stream()
                .collect(Collectors.toMap(RoadmapStepTemplate::getId, Function.identity()));
        List<PlanStep> steps = planStepRepository.findByPlanId(plan.getId());

        List<BudgetStatusCalculator.AmountStep> amountSteps = steps.stream()
                .filter(step -> {
                    RoadmapStepTemplate template = templateById.get(step.getStepTemplateId());
                    return template != null && template.hasCost();
                })
                .sorted(Comparator.comparing(step -> templateById.get(step.getStepTemplateId()).getSortOrder()))
                .map(step -> {
                    RoadmapStepTemplate template = templateById.get(step.getStepTemplateId());
                    return new BudgetStatusCalculator.AmountStep(
                            step.getId(), template.getCode(), template.getCostKind().name(),
                            step.isCompleted(), step.getActualAmount(), step.getEstimatedMin(), step.getEstimatedMax());
                })
                .toList();
        boolean allStepsCompleted = !steps.isEmpty() && steps.stream().allMatch(PlanStep::isCompleted);

        return BudgetStatusCalculator.calculate(plan.getBudgetAmount(), amountSteps, allStepsCompleted);
    }

    private void assign(PlanTodo todo, String assignee, UUID userId, List<PlanMember> members, OffsetDateTime now) {
        switch (assignee) {
            case "ME" -> todo.assign(AssignmentKind.USER, userId, userId, now);
            case "PARTNER" -> {
                UUID partnerId = otherMember(members, userId)
                        .orElseThrow(() -> new IllegalArgumentException("연결된 짝꿍이 없어요. 짝꿍을 초대한 뒤 맡길 수 있어요."));
                todo.assign(AssignmentKind.USER, partnerId, userId, now);
            }
            case "BOTH" -> todo.assign(AssignmentKind.BOTH, null, userId, now);
            case "UNASSIGNED" -> todo.assign(AssignmentKind.UNASSIGNED, null, userId, now);
            default -> throw new IllegalArgumentException("올바른 담당을 선택해주세요.");
        }
    }

    /** 내 계획. 고칠 때는 계획 행을 잠근다(P-04, PlanService.overwriteMine과 같은 잠금). */
    private Plan myPlan(UUID userId, boolean forUpdate) {
        PlanMember member = planMemberRepository.findByUserId(userId)
                .orElseThrow(PlanNotFoundException::new);
        Optional<Plan> plan = forUpdate
                ? planRepository.findByIdAndStatusForUpdate(member.getPlanId(), Plan.Status.ACTIVE)
                : planRepository.findByIdAndStatus(member.getPlanId(), Plan.Status.ACTIVE);
        return plan.orElseThrow(PlanNotFoundException::new);
    }

    private PlanStep myStep(Plan plan, UUID stepId) {
        return planStepRepository.findByIdAndPlanId(stepId, plan.getId())
                .orElseThrow(RoadmapItemNotFoundException::step);
    }

    private RoadmapStepTemplate template(PlanStep step) {
        return stepTemplateRepository.findById(step.getStepTemplateId())
                .orElseThrow(() -> new IllegalStateException("로드맵 단계 정의를 찾을 수 없습니다."));
    }

    private static Optional<UUID> otherMember(List<PlanMember> members, UUID userId) {
        return members.stream()
                .map(PlanMember::getUserId)
                .filter(id -> !id.equals(userId))
                .findFirst();
    }

    private static TodoResponse toTodoResponse(PlanTodo todo, UUID viewerId, List<PlanMember> members) {
        return new TodoResponse(
                todo.getId(),
                todo.getPlanStepId(),
                todo.getTitleSnapshot(),
                todo.isCompleted(),
                todo.getCompletedAt(),
                assigneeFor(todo, viewerId, members),
                todo.getAssignedUserId(),
                todo.getUpdatedBy(),
                todo.getUpdatedAt()
        );
    }

    /** 담당을 보는 사람 기준으로 바꾼다. 계획을 떠난 회원은 지금 짝꿍으로 표시하지 않는다(db.md 4.30). */
    static String assigneeFor(PlanTodo todo, UUID viewerId, List<PlanMember> members) {
        return switch (todo.getAssignmentKind()) {
            case UNASSIGNED -> "UNASSIGNED";
            case BOTH -> "BOTH";
            case USER -> {
                UUID assigned = todo.getAssignedUserId();
                if (assigned.equals(viewerId)) {
                    yield "ME";
                }
                boolean currentMember = members.stream().anyMatch(m -> m.getUserId().equals(assigned));
                yield currentMember ? "PARTNER" : "FORMER_MEMBER";
            }
        };
    }

    private static RoadmapStepResponse toStepResponse(PlanStep step, RoadmapStepTemplate template) {
        return new RoadmapStepResponse(
                step.getId(),
                template.getCode(),
                template.getCostKind().name(),
                template.hasCost() ? new RoadmapStepResponse.Amount(step.getEstimatedMin(), step.getEstimatedMax()) : null,
                step.getActualAmount(),
                step.isCompleted(),
                step.getCompletedAt(),
                step.getCompletedBy(),
                step.getUpdatedBy(),
                step.getUpdatedAt()
        );
    }
}
