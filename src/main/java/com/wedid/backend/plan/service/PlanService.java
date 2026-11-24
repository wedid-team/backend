package com.wedid.backend.plan.service;

import com.wedid.backend.estimate.entity.CalculationPolicy;
import com.wedid.backend.estimate.entity.EstimateRun;
import com.wedid.backend.estimate.entity.SdmBudgetBand;
import com.wedid.backend.estimate.region.Region;
import com.wedid.backend.estimate.region.RegionRepository;
import com.wedid.backend.estimate.repository.EstimateRunRepository;
import com.wedid.backend.estimate.repository.SdmBudgetBandRepository;
import com.wedid.backend.plan.dto.PlanRequest;
import com.wedid.backend.plan.dto.PlanResponse;
import com.wedid.backend.plan.entity.DatePrecision;
import com.wedid.backend.plan.entity.GuestChoice;
import com.wedid.backend.plan.entity.Plan;
import com.wedid.backend.plan.entity.PlanConditions;
import com.wedid.backend.plan.entity.PlanMember;
import com.wedid.backend.plan.entity.PlanSchedule;
import com.wedid.backend.plan.repository.PlanMemberRepository;
import com.wedid.backend.plan.repository.PlanRepository;
import com.wedid.backend.roadmap.entity.PlanStep;
import com.wedid.backend.roadmap.entity.PlanTodo;
import com.wedid.backend.roadmap.entity.RoadmapStepTemplate;
import com.wedid.backend.roadmap.entity.TodoTemplate;
import com.wedid.backend.roadmap.repository.PlanStepRepository;
import com.wedid.backend.roadmap.repository.PlanTodoRepository;
import com.wedid.backend.roadmap.repository.RoadmapStepTemplateRepository;
import com.wedid.backend.roadmap.repository.TodoTemplateRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 계획 저장 · 조회 · 덮어쓰기.
 * <ul>
 *   <li>내 계획은 plan_members로 찾으므로 계획을 만든 사람과 합류한 짝꿍이 같은 계획을 본다.</li>
 *   <li>부부당 계획은 하나다(P-01). 이미 계획이 있으면 새로 만들지 않고 409로 알린다(C-10).</li>
 *   <li>조건을 바꾸면 같은 계획에 덮어쓴다. 새 계산 기록을 만들고, 완료하지 않은 단계의 예상 비용만 갱신한다.
 *       완료 기록 · 실제 계약 금액 · 할 일 · 짝꿍 연결은 그대로 둔다(P-02 · db.md 5.3).</li>
 * </ul>
 */
@Service
public class PlanService {

    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

    private final PlanRepository planRepository;
    private final PlanMemberRepository planMemberRepository;
    private final PlanStepRepository planStepRepository;
    private final PlanTodoRepository planTodoRepository;
    private final RoadmapStepTemplateRepository stepTemplateRepository;
    private final TodoTemplateRepository todoTemplateRepository;
    private final EstimateRunRepository estimateRunRepository;
    private final SdmBudgetBandRepository bandRepository;
    private final RegionRepository regionRepository;
    private final PlanPolicyResolver policyResolver;
    private final PlanEstimateRecorder estimateRecorder;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    public PlanService(
            PlanRepository planRepository,
            PlanMemberRepository planMemberRepository,
            PlanStepRepository planStepRepository,
            PlanTodoRepository planTodoRepository,
            RoadmapStepTemplateRepository stepTemplateRepository,
            TodoTemplateRepository todoTemplateRepository,
            EstimateRunRepository estimateRunRepository,
            SdmBudgetBandRepository bandRepository,
            RegionRepository regionRepository,
            PlanPolicyResolver policyResolver,
            PlanEstimateRecorder estimateRecorder,
            JsonMapper jsonMapper,
            Clock clock
    ) {
        this.planRepository = planRepository;
        this.planMemberRepository = planMemberRepository;
        this.planStepRepository = planStepRepository;
        this.planTodoRepository = planTodoRepository;
        this.stepTemplateRepository = stepTemplateRepository;
        this.todoTemplateRepository = todoTemplateRepository;
        this.estimateRunRepository = estimateRunRepository;
        this.bandRepository = bandRepository;
        this.regionRepository = regionRepository;
        this.policyResolver = policyResolver;
        this.estimateRecorder = estimateRecorder;
        this.jsonMapper = jsonMapper;
        this.clock = clock;
    }

    /** 첫 계획 저장. 계획 · 소유자 멤버십 · 계산 기록 · 로드맵 6단계 · 기본 할 일을 한 번에 만든다. */
    @Transactional
    public PlanResponse create(UUID userId, PlanRequest request) {
        if (planMemberRepository.existsByUserId(userId)) {
            throw new PlanAlreadyExistsException();
        }

        OffsetDateTime now = OffsetDateTime.now(clock);
        CalculationPolicy policy = policyResolver.currentPolicy();
        SdmBudgetBand band = policyResolver.band(policy, request.sdmBandCode(), null);
        PlanConditions conditions = toConditions(request, band, null);

        Plan plan = Plan.create(userId, conditions, now);
        try {
            // 동시에 두 번 저장해도 부분 UNIQUE(활성 소유 계획) · plan_members(user_id) UNIQUE가 막는다.
            planRepository.saveAndFlush(plan);
            planMemberRepository.saveAndFlush(PlanMember.owner(plan.getId(), userId));
        } catch (DataIntegrityViolationException e) {
            // FK 위반 등 다른 무결성 오류까지 "이미 저장된 계획"으로 숨기지 않는다
            if (violatesOnePlanPerUser(e)) {
                throw new PlanAlreadyExistsException();
            }
            throw e;
        }

        EstimateRun estimate = estimateRunRepository.save(
                estimateRecorder.record(plan.getId(), userId, request, conditions, policy, band, now));
        plan.pointTo(estimate.getId());

        List<RoadmapStepTemplate> templates = stepTemplateRepository.findAllByOrderBySortOrderAsc();
        LocalDate weddingDate = conditions.schedule().effectiveWeddingDate();
        List<PlanStep> steps = planStepRepository.saveAll(templates.stream()
                .map(template -> PlanStep.create(plan.getId(), template, weddingDate, estimate, userId, now))
                .toList());
        planTodoRepository.saveAll(createTodos(steps, userId, now));

        return toResponse(plan, PlanMember.Role.OWNER, band, estimate, steps, templates);
    }

    /** 내 준비 공간 조회. 계획을 만든 사람과 합류한 짝꿍 모두 같은 계획을 본다. */
    @Transactional(readOnly = true)
    public PlanResponse getMine(UUID userId) {
        PlanMember member = planMemberRepository.findByUserId(userId)
                .orElseThrow(PlanNotFoundException::new);
        Plan plan = planRepository.findByIdAndStatus(member.getPlanId(), Plan.Status.ACTIVE)
                .orElseThrow(PlanNotFoundException::new);
        return toResponse(plan, member.getRole());
    }

    /**
     * 조건 덮어쓰기(P-01 · C-10 [방금 만든 계획으로 바꾸기] · E-11 조건 수정 · D-20 예산대 변경).
     * 계획 ID는 그대로 두고 새 계산 기록을 만들어 현재 결과로 가리킨다.
     */
    @Transactional
    public PlanResponse overwriteMine(UUID userId, PlanRequest request) {
        PlanMember member = planMemberRepository.findByUserId(userId)
                .orElseThrow(PlanNotFoundException::new);
        Plan plan = planRepository.findByIdAndStatusForUpdate(member.getPlanId(), Plan.Status.ACTIVE)
                .orElseThrow(PlanNotFoundException::new);

        OffsetDateTime now = OffsetDateTime.now(clock);
        CalculationPolicy policy = policyResolver.currentPolicy();
        String currentBandCode = bandRepository.findById(plan.getSelectedBandId())
                .map(SdmBudgetBand::getCode)
                .orElse(null);
        // 정책 버전이 바뀌었어도 같은 코드의 예산대를 현재 정책에서 다시 고른다(band와 policy는 같은 버전).
        SdmBudgetBand band = policyResolver.band(policy, request.sdmBandCode(), currentBandCode);
        PlanConditions conditions = toConditions(request, band, plan.schedule());

        EstimateRun estimate = estimateRunRepository.save(
                estimateRecorder.record(plan.getId(), userId, request, conditions, policy, band, now));
        plan.overwrite(conditions, userId, now);
        plan.pointTo(estimate.getId());

        List<RoadmapStepTemplate> templates = stepTemplateRepository.findAllByOrderBySortOrderAsc();
        Map<Short, RoadmapStepTemplate> templateById = byId(templates);
        LocalDate weddingDate = conditions.schedule().effectiveWeddingDate();
        List<PlanStep> steps = planStepRepository.findByPlanId(plan.getId());
        for (PlanStep step : steps) {
            step.applyConditions(templateById.get(step.getStepTemplateId()), weddingDate, estimate, userId, now);
        }

        return toResponse(plan, member.getRole(), band, estimate, steps, templates);
    }

    /** 부부당 계획 1개(P-01)를 지키는 UNIQUE 제약. sql/022 · sql/023 */
    static final Set<String> ONE_PLAN_PER_USER_CONSTRAINTS = Set.of(
            "uq_plans_active_owner",
            "uq_plan_members_user",
            "uq_plan_members_plan_user"
    );

    /** 위반된 제약 조건 이름이 1인 1계획 UNIQUE 제약 중 하나인지. 이름을 알 수 없으면 false. */
    static boolean violatesOnePlanPerUser(DataIntegrityViolationException e) {
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation) {
                String name = violation.getConstraintName();
                return name != null && ONE_PLAN_PER_USER_CONSTRAINTS.contains(name.toLowerCase(Locale.ROOT));
            }
        }
        return false;
    }

    private PlanConditions toConditions(PlanRequest request, SdmBudgetBand band, PlanSchedule previous) {
        Short regionId = regionRepository.findByCode(request.regionCode())
                .map(Region::getId)
                .orElseThrow(() -> new IllegalArgumentException("올바른 지역을 선택해주세요."));

        PlanSchedule schedule = PlanSchedule.resolve(
                DatePrecision.from(request.weddingDateType()),
                request.weddingDate(),
                request.weddingYear(),
                request.weddingMonth(),
                LocalDate.now(clock.withZone(SERVICE_ZONE)),
                previous
        );

        return new PlanConditions(
                regionId,
                GuestChoice.fromRangeCode(request.guestRangeCode()),
                schedule,
                request.budget() == null ? null : BigDecimal.valueOf(request.budget()),
                request.normalizedMoods(),
                band.getId()
        );
    }

    /** 기본 할 일 템플릿이 있으면 단계마다 할 일을 만든다. 템플릿이 없으면 만들지 않는다. */
    private List<PlanTodo> createTodos(List<PlanStep> steps, UUID userId, OffsetDateTime now) {
        Map<Short, UUID> stepIdByTemplate = steps.stream()
                .collect(Collectors.toMap(PlanStep::getStepTemplateId, PlanStep::getId));

        List<PlanTodo> todos = new ArrayList<>();
        for (TodoTemplate template : todoTemplateRepository.findAllByOrderByStepTemplateIdAscSortOrderAsc()) {
            UUID stepId = stepIdByTemplate.get(template.getStepTemplateId());
            if (stepId != null) {
                todos.add(PlanTodo.create(stepId, template, userId, now));
            }
        }
        return todos;
    }

    private PlanResponse toResponse(Plan plan, PlanMember.Role role) {
        SdmBudgetBand band = bandRepository.findById(plan.getSelectedBandId())
                .orElseThrow(() -> new IllegalStateException("계획의 스드메 예산대를 찾을 수 없습니다."));
        EstimateRun estimate = plan.getCurrentEstimateId() == null ? null
                : estimateRunRepository.findById(plan.getCurrentEstimateId()).orElse(null);
        return toResponse(plan, role, band, estimate,
                planStepRepository.findByPlanId(plan.getId()),
                stepTemplateRepository.findAllByOrderBySortOrderAsc());
    }

    private PlanResponse toResponse(
            Plan plan,
            PlanMember.Role role,
            SdmBudgetBand band,
            EstimateRun estimate,
            List<PlanStep> steps,
            List<RoadmapStepTemplate> templates
    ) {
        PlanSchedule schedule = plan.schedule();
        Region region = regionRepository.findById(plan.getRegionId()).orElse(null);
        LocalDate month = schedule.weddingMonth();

        PlanResponse.Conditions conditions = new PlanResponse.Conditions(
                region == null ? null : region.getCode(),
                region == null ? null : region.getName(),
                plan.getGuestChoice().rangeCode(),
                schedule.datePrecision().toWeddingDateType(),
                schedule.weddingDate(),
                month == null ? null : month.getYear(),
                month == null ? null : month.getMonthValue(),
                schedule.effectiveWeddingDate(),
                schedule.isEstimatedDate(),
                plan.getBudgetAmount(),
                plan.getPreferredMoods()
        );

        PlanResponse.SdmBand sdmBand = new PlanResponse.SdmBand(
                band.getCode(), band.getLabel(), band.getLowerAmount(), band.getUpperAmount());

        Map<Short, RoadmapStepTemplate> templateById = byId(templates);
        List<PlanResponse.Step> stepResponses = steps.stream()
                .filter(step -> templateById.containsKey(step.getStepTemplateId()))
                .sorted(Comparator.comparing(step -> templateById.get(step.getStepTemplateId()).getSortOrder()))
                .map(step -> toStep(step, templateById.get(step.getStepTemplateId())))
                .toList();

        return new PlanResponse(
                plan.getId(),
                role.name(),
                conditions,
                sdmBand,
                estimate == null ? null : toEstimate(estimate),
                stepResponses,
                plan.getConditionsUpdatedAt(),
                plan.getScheduleUpdatedAt(),
                plan.getUpdatedBy(),
                plan.getUpdatedAt()
        );
    }

    private PlanResponse.Estimate toEstimate(EstimateRun estimate) {
        JsonNode result = jsonMapper.readTree(estimate.getResultSnapshot());
        JsonNode venue = result.path("venue");
        JsonNode sdm = result.path("sdm");
        EstimateRun.Range venueRange = estimate.getVenue();

        return new PlanResponse.Estimate(
                estimate.getId(),
                estimate.getCalculationStatus(),
                estimate.getCreatedAt(),
                new PlanResponse.Venue(
                        venueRange.min(),
                        venueRange.max(),
                        textOrNull(venue, "sourceType"),
                        textOrNull(venue, "referenceMonth"),
                        textOrNull(venue, "sourceRegion"),
                        textOrNull(venue, "sourceUrl"),
                        venue.path("sampleCount").isNumber() ? venue.path("sampleCount").asInt() : null
                ),
                new PlanResponse.Sdm(
                        estimate.getSdm().min(),
                        estimate.getSdm().max(),
                        textOrNull(sdm, "sourceType"),
                        // 이전 기록에는 이유가 없으므로 금액이 없으면 경계 미정으로 본다
                        estimate.getSdm().isKnown() ? null
                                : sdm.has("pendingReason") ? textOrNull(sdm, "pendingReason") : "TIER_BOUNDARY_PENDING"),
                amount(estimate.getTotal())
        );
    }

    private static PlanResponse.Step toStep(PlanStep step, RoadmapStepTemplate template) {
        boolean hasCost = template.getCostKind() != RoadmapStepTemplate.CostKind.NONE;
        return new PlanResponse.Step(
                step.getId(),
                template.getCode(),
                template.getTitle(),
                template.getSortOrder(),
                template.getCostKind().name(),
                step.getRecommendedStartDate(),
                step.getRecommendedEndDate(),
                hasCost ? new PlanResponse.Amount(step.getEstimatedMin(), step.getEstimatedMax()) : null,
                step.getActualAmount(),
                step.isCompleted(),
                step.getCompletedAt()
        );
    }

    private static PlanResponse.Amount amount(EstimateRun.Range range) {
        return new PlanResponse.Amount(range.min(), range.max());
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isString() ? value.asString() : null;
    }

    private static Map<Short, RoadmapStepTemplate> byId(List<RoadmapStepTemplate> templates) {
        return templates.stream().collect(Collectors.toMap(RoadmapStepTemplate::getId, Function.identity()));
    }
}
