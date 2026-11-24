package com.wedid.backend.roadmap.entity;

import com.wedid.backend.global.entity.AssignedIdEntity;
import com.wedid.backend.estimate.entity.EstimateRun;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 계획별 로드맵 단계와 실제 계약 금액.
 * 조건이 바뀌어도 완료한 단계의 예상 비용 · 실제 금액 · 완료 기록은 그대로 둔다(P-02).
 */
@Entity
@Table(name = "plan_steps", schema = "public")
public class PlanStep extends AssignedIdEntity {

    @Id
    private UUID id;

    @Column(name = "plan_id", nullable = false, updatable = false)
    private UUID planId;

    @Column(name = "step_template_id", nullable = false, updatable = false)
    private Short stepTemplateId;

    @Column(name = "recommended_start_date", nullable = false)
    private LocalDate recommendedStartDate;

    @Column(name = "recommended_end_date", nullable = false)
    private LocalDate recommendedEndDate;

    @Column(name = "estimated_min")
    private BigDecimal estimatedMin;

    @Column(name = "estimated_max")
    private BigDecimal estimatedMax;

    @Column(name = "estimate_run_id")
    private UUID estimateRunId;

    @Column(name = "actual_amount")
    private BigDecimal actualAmount;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Column(name = "completed_by")
    private UUID completedBy;

    @Column(name = "updated_by", nullable = false)
    private UUID updatedBy;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected PlanStep() {
    }

    public static PlanStep create(
            UUID planId,
            RoadmapStepTemplate template,
            LocalDate weddingDate,
            EstimateRun estimate,
            UUID userId,
            OffsetDateTime now
    ) {
        PlanStep step = new PlanStep();
        step.id = UUID.randomUUID();
        step.planId = planId;
        step.stepTemplateId = template.getId();
        step.reschedule(template, weddingDate);
        step.applyEstimate(template, estimate);
        step.updatedBy = userId;
        step.updatedAt = now;
        return step;
    }

    /**
     * 새 조건을 반영한다. 권장 기간은 모든 단계에서 다시 계산하고,
     * 예상 비용은 완료하지 않은 단계만 새 계산 결과로 바꾼다(db.md 5.3).
     * 완료 여부 · 실제 계약 금액은 건드리지 않는다.
     */
    public void applyConditions(
            RoadmapStepTemplate template,
            LocalDate weddingDate,
            EstimateRun estimate,
            UUID userId,
            OffsetDateTime now
    ) {
        reschedule(template, weddingDate);
        if (!isCompleted()) {
            applyEstimate(template, estimate);
        }
        touch(userId, now);
    }

    private void reschedule(RoadmapStepTemplate template, LocalDate weddingDate) {
        this.recommendedStartDate = template.recommendedStartDate(weddingDate);
        this.recommendedEndDate = template.recommendedEndDate(weddingDate);
    }

    private void applyEstimate(RoadmapStepTemplate template, EstimateRun estimate) {
        EstimateRun.Range range = switch (template.getCostKind()) {
            case VENUE -> estimate.getVenue();
            case SDM -> estimate.getSdm();
            case NONE -> null;
        };
        if (range == null) {
            return;
        }
        this.estimatedMin = range.min();
        this.estimatedMax = range.max();
        this.estimateRunId = estimate.getId();
    }

    /**
     * 실제 계약 금액을 입력하거나 고친다. 완료한 단계도 고칠 수 있다(R-04).
     * 예상 비용과 완료 상태는 그대로 둔다(db.md 5.3).
     */
    public void recordActualAmount(RoadmapStepTemplate template, BigDecimal amount, UUID userId, OffsetDateTime now) {
        if (!template.hasCost()) {
            throw new IllegalArgumentException("실제 계약 금액은 예식장 · 스드메 단계에만 입력할 수 있어요.");
        }
        this.actualAmount = amount;
        touch(userId, now);
    }

    /**
     * 단계를 완료한다. 금액 단계(예식장 · 스드메)는 실제 계약 금액이 있어야 한다(R-09).
     * 할 일을 모두 체크하지 않아도 완료할 수 있고, 완료 순서도 제한하지 않는다(R-02).
     * 이미 완료한 단계면 처음 완료한 기록을 그대로 둔다.
     */
    public void complete(RoadmapStepTemplate template, UUID userId, OffsetDateTime now) {
        if (isCompleted()) {
            return;
        }
        if (template.hasCost() && actualAmount == null) {
            throw new IllegalArgumentException("실제 계약 금액을 입력해야 단계를 완료할 수 있어요.");
        }
        this.completedAt = now;
        this.completedBy = userId;
        touch(userId, now);
    }

    /**
     * 완료를 취소한다. 실제 계약 금액은 지우지 않는다.
     * 다시 미완료 단계가 되므로 예상 비용은 계획의 현재 계산 결과로 바로 맞춘다(새로 계산하지는 않는다).
     */
    public void reopen(RoadmapStepTemplate template, EstimateRun currentEstimate, UUID userId, OffsetDateTime now) {
        if (!isCompleted()) {
            return;
        }
        this.completedAt = null;
        this.completedBy = null;
        if (currentEstimate != null) {
            applyEstimate(template, currentEstimate);
        }
        touch(userId, now);
    }

    private void touch(UUID userId, OffsetDateTime now) {
        this.updatedBy = userId;
        this.updatedAt = now;
    }

    public boolean isCompleted() {
        return completedAt != null;
    }

    public UUID getId() {
        return id;
    }

    public UUID getPlanId() {
        return planId;
    }

    public Short getStepTemplateId() {
        return stepTemplateId;
    }

    public LocalDate getRecommendedStartDate() {
        return recommendedStartDate;
    }

    public LocalDate getRecommendedEndDate() {
        return recommendedEndDate;
    }

    public BigDecimal getEstimatedMin() {
        return estimatedMin;
    }

    public BigDecimal getEstimatedMax() {
        return estimatedMax;
    }

    public UUID getEstimateRunId() {
        return estimateRunId;
    }

    public BigDecimal getActualAmount() {
        return actualAmount;
    }

    public OffsetDateTime getCompletedAt() {
        return completedAt;
    }

    public UUID getCompletedBy() {
        return completedBy;
    }

    public UUID getUpdatedBy() {
        return updatedBy;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
