package com.wedid.backend.roadmap.service;

import com.wedid.backend.estimate.entity.EstimateRun;
import com.wedid.backend.estimate.repository.EstimateRunRepository;
import com.wedid.backend.plan.entity.Plan;
import com.wedid.backend.plan.entity.PlanMember;
import com.wedid.backend.plan.repository.PlanMemberRepository;
import com.wedid.backend.plan.repository.PlanRepository;
import com.wedid.backend.plan.service.PlanNotFoundException;
import com.wedid.backend.roadmap.dto.BudgetStatusResponse;
import com.wedid.backend.plan.entity.PlanSchedule;
import com.wedid.backend.roadmap.dto.RoadmapResponse;
import com.wedid.backend.roadmap.dto.RoadmapStepResponse;
import com.wedid.backend.roadmap.entity.TodoTemplate;
import com.wedid.backend.roadmap.repository.TodoTemplateRepository;
import com.wedid.backend.user.entity.OAuthAccount;
import com.wedid.backend.user.entity.OAuthProvider;
import com.wedid.backend.user.entity.User;
import com.wedid.backend.user.repository.OAuthAccountRepository;
import com.wedid.backend.user.repository.UserRepository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
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
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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

    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

    private final PlanRepository planRepository;
    private final PlanMemberRepository planMemberRepository;
    private final PlanStepRepository planStepRepository;
    private final PlanTodoRepository planTodoRepository;
    private final RoadmapStepTemplateRepository stepTemplateRepository;
    private final EstimateRunRepository estimateRunRepository;
    private final TodoTemplateRepository todoTemplateRepository;
    private final UserRepository userRepository;
    private final OAuthAccountRepository oauthAccountRepository;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    public RoadmapService(
            PlanRepository planRepository,
            PlanMemberRepository planMemberRepository,
            PlanStepRepository planStepRepository,
            PlanTodoRepository planTodoRepository,
            RoadmapStepTemplateRepository stepTemplateRepository,
            EstimateRunRepository estimateRunRepository,
            TodoTemplateRepository todoTemplateRepository,
            UserRepository userRepository,
            OAuthAccountRepository oauthAccountRepository,
            JsonMapper jsonMapper,
            Clock clock
    ) {
        this.planRepository = planRepository;
        this.planMemberRepository = planMemberRepository;
        this.planStepRepository = planStepRepository;
        this.planTodoRepository = planTodoRepository;
        this.stepTemplateRepository = stepTemplateRepository;
        this.estimateRunRepository = estimateRunRepository;
        this.todoTemplateRepository = todoTemplateRepository;
        this.userRepository = userRepository;
        this.oauthAccountRepository = oauthAccountRepository;
        this.jsonMapper = jsonMapper;
        this.clock = clock;
    }

    /**
     * 준비 로드맵 전체: 6단계와 단계별 할 일, D-day, 다음 단계, 멤버.
     * 담당(나 / 짝꿍)은 조회한 사람 기준으로 돌려준다.
     */
    @Transactional(readOnly = true)
    public RoadmapResponse getRoadmap(UUID userId) {
        Plan plan = myPlan(userId, false);
        LocalDate today = LocalDate.now(clock.withZone(SERVICE_ZONE));
        PlanSchedule schedule = plan.schedule();
        List<PlanMember> members = planMemberRepository.findByPlanId(plan.getId());

        Map<Short, RoadmapStepTemplate> templateById = stepTemplateRepository.findAll().stream()
                .collect(Collectors.toMap(RoadmapStepTemplate::getId, Function.identity()));
        List<PlanStep> steps = planStepRepository.findByPlanId(plan.getId()).stream()
                .filter(step -> templateById.containsKey(step.getStepTemplateId()))
                .sorted(Comparator.comparing(step -> templateById.get(step.getStepTemplateId()).getSortOrder()))
                .toList();
        Map<UUID, List<TodoResponse>> todosByStep = todosByStep(steps, userId, members);

        List<RoadmapResponse.Step> stepResponses = steps.stream()
                .map(step -> toRoadmapStep(step, templateById.get(step.getStepTemplateId()), plan, today,
                        todosByStep.getOrDefault(step.getId(), List.of())))
                .toList();
        UUID nextStepId = steps.stream()
                .filter(step -> !step.isCompleted())
                .map(PlanStep::getId)
                .findFirst()
                .orElse(null);
        int completedCount = (int) steps.stream().filter(PlanStep::isCompleted).count();

        Map<UUID, RoadmapResponse.Member> memberById = toMembers(members);
        RoadmapResponse.Member partner = members.stream()
                .filter(member -> !member.getUserId().equals(userId))
                .map(member -> memberById.get(member.getUserId()))
                .findFirst()
                .orElse(null);

        return new RoadmapResponse(
                plan.getId(),
                new RoadmapResponse.Wedding(
                        schedule.datePrecision().toWeddingDateType(),
                        schedule.effectiveWeddingDate(),
                        schedule.isEstimatedDate(),
                        ChronoUnit.DAYS.between(today, schedule.effectiveWeddingDate())),
                nextStepId,
                completedCount,
                stepResponses,
                memberById.get(userId),
                partner,
                plan.getScheduleUpdatedAt()
        );
    }

    private Map<UUID, List<TodoResponse>> todosByStep(List<PlanStep> steps, UUID viewerId, List<PlanMember> members) {
        List<PlanTodo> todos = planTodoRepository.findByPlanStepIdIn(steps.stream().map(PlanStep::getId).toList());
        Map<Long, Integer> sortOrderByTemplate = todoTemplateRepository
                .findAllById(todos.stream().map(PlanTodo::getTodoTemplateId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(TodoTemplate::getId, TodoTemplate::getSortOrder));

        return todos.stream()
                .sorted(Comparator
                        .comparing((PlanTodo todo) -> sortOrderByTemplate.getOrDefault(todo.getTodoTemplateId(), Integer.MAX_VALUE))
                        .thenComparing(PlanTodo::getTitleSnapshot))
                .collect(Collectors.groupingBy(
                        PlanTodo::getPlanStepId,
                        Collectors.mapping(todo -> toTodoResponse(todo, viewerId, members), Collectors.toList())));
    }

    private RoadmapResponse.Step toRoadmapStep(
            PlanStep step,
            RoadmapStepTemplate template,
            Plan plan,
            LocalDate today,
            List<TodoResponse> todos
    ) {
        boolean overdue = !step.isCompleted() && step.getRecommendedEndDate().isBefore(today);
        OffsetDateTime reviewedFor = step.getOverdueReviewedFor();
        boolean overdueReviewed = reviewedFor != null && !reviewedFor.isBefore(plan.getScheduleUpdatedAt());

        return new RoadmapResponse.Step(
                step.getId(),
                template.getCode(),
                template.getTitle(),
                null,
                template.getSortOrder(),
                template.getCostKind().name(),
                template.getDaysBeforeStart(),
                template.getDaysBeforeEnd(),
                step.getRecommendedStartDate(),
                step.getRecommendedEndDate(),
                template.hasCost() ? new RoadmapResponse.Amount(step.getEstimatedMin(), step.getEstimatedMax()) : null,
                step.getActualAmount(),
                step.isCompleted(),
                step.getCompletedAt(),
                step.getCompletedBy(),
                overdue,
                overdueReviewed,
                textItems(template.getDecisionItems()),
                textItems(template.getGuidanceItems()),
                null,
                todos,
                step.getUpdatedBy(),
                step.getUpdatedAt()
        );
    }

    /** 템플릿 JSON 배열의 문자열 항목. 비어 있거나 읽을 수 없으면 빈 목록. */
    private List<String> textItems(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        List<String> items = new ArrayList<>();
        for (JsonNode node : jsonMapper.readTree(json)) {
            if (node.isString()) {
                items.add(node.asString());
            }
        }
        return List.copyOf(items);
    }

    private Map<UUID, RoadmapResponse.Member> toMembers(List<PlanMember> members) {
        List<UUID> userIds = members.stream().map(PlanMember::getUserId).toList();
        Map<UUID, User> userById = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        Map<UUID, OAuthProvider> providerByUser = oauthAccountRepository.findByUserIdIn(userIds).stream()
                .sorted(Comparator.comparing(OAuthAccount::getProvider))
                .collect(Collectors.toMap(OAuthAccount::getUserId, OAuthAccount::getProvider, (first, second) -> first));

        Map<UUID, RoadmapResponse.Member> result = new HashMap<>();
        for (PlanMember member : members) {
            User user = userById.get(member.getUserId());
            OAuthProvider provider = providerByUser.get(member.getUserId());
            result.put(member.getUserId(), new RoadmapResponse.Member(
                    member.getUserId(),
                    user == null ? null : user.getDisplayName(),
                    member.getRole().name(),
                    provider == null ? null : provider.name(),
                    member.getJoinedAt()));
        }
        return result;
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
        // 템플릿마다 계획 단계가 하나씩 있어야 한다. 빠진 단계가 있으면 완료로 보지 않는다
        Set<Short> planTemplateIds = steps.stream().map(PlanStep::getStepTemplateId).collect(Collectors.toSet());
        boolean amountStepsMissing = templateById.values().stream()
                .anyMatch(template -> template.hasCost() && !planTemplateIds.contains(template.getId()));
        boolean allStepsCompleted = planTemplateIds.containsAll(templateById.keySet())
                && steps.stream().allMatch(PlanStep::isCompleted);

        return BudgetStatusCalculator.calculate(plan.getBudgetAmount(), amountSteps, amountStepsMissing, allStepsCompleted);
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
