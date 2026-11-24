package com.wedid.backend.plan.entity;

import com.wedid.backend.global.entity.AssignedIdEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 부부의 현재 계획. 부부당 하나이며(P-01), 조건을 바꾸면 같은 계획에 덮어쓴다.
 * 계산 결과는 estimate_runs에 고정 기록으로 남기고 계획은 현재 결과만 가리킨다.
 */
@Entity
@Table(name = "plans", schema = "public")
public class Plan extends AssignedIdEntity {

    public enum Status {
        ACTIVE, REPLACED
    }

    @Id
    private UUID id;

    @Column(name = "owner_user_id", nullable = false, updatable = false)
    private UUID ownerUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Column(name = "region_id", nullable = false)
    private Short regionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "guest_choice", nullable = false)
    private GuestChoice guestChoice;

    @Enumerated(EnumType.STRING)
    @Column(name = "date_precision", nullable = false)
    private DatePrecision datePrecision;

    @Column(name = "wedding_date")
    private LocalDate weddingDate;

    @Column(name = "wedding_month")
    private LocalDate weddingMonth;

    @Column(name = "assumption_date", nullable = false)
    private LocalDate assumptionDate;

    @Column(name = "effective_wedding_date", nullable = false)
    private LocalDate effectiveWeddingDate;

    @Column(name = "budget_amount")
    private BigDecimal budgetAmount;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "preferred_moods", nullable = false)
    private String[] preferredMoods;

    @Column(name = "selected_band_id", nullable = false)
    private Long selectedBandId;

    @Column(name = "current_estimate_id")
    private UUID currentEstimateId;

    @Column(name = "conditions_updated_at", nullable = false)
    private OffsetDateTime conditionsUpdatedAt;

    @Column(name = "schedule_updated_at", nullable = false)
    private OffsetDateTime scheduleUpdatedAt;

    @Column(name = "updated_by", nullable = false)
    private UUID updatedBy;

    @Column(name = "created_at", nullable = false,
            insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected Plan() {
    }

    /** current_estimate_id는 계산 기록을 만든 뒤 {@link #pointTo}로 채운다(db.md 9 저장 순서). */
    public static Plan create(UUID ownerUserId, PlanConditions conditions, OffsetDateTime now) {
        Plan plan = new Plan();
        plan.id = UUID.randomUUID();
        plan.ownerUserId = ownerUserId;
        plan.status = Status.ACTIVE;
        plan.write(conditions);
        plan.conditionsUpdatedAt = now;
        plan.scheduleUpdatedAt = now;
        plan.updatedBy = ownerUserId;
        plan.updatedAt = now;
        return plan;
    }

    /** 조건을 새 값으로 덮어쓴다. 예식 일정이 바뀌었을 때만 schedule_updated_at을 옮긴다. */
    public void overwrite(PlanConditions conditions, UUID userId, OffsetDateTime now) {
        boolean scheduleChanged = conditions.schedule().differsFrom(schedule());
        write(conditions);
        this.conditionsUpdatedAt = now;
        if (scheduleChanged) {
            this.scheduleUpdatedAt = now;
        }
        this.updatedBy = userId;
        this.updatedAt = now;
    }

    /**
     * 짝꿍 초대로 다른 계획에 합류하면서 내 계획을 정리한다(P-05 · db.md 5.2).
     * 기록은 지우지 않고 REPLACED로 두며, 일반 조회에서는 제외된다.
     * updated_by는 조건을 바꾼 사람만 가리키므로 건드리지 않는다.
     */
    public void replace(OffsetDateTime now) {
        this.status = Status.REPLACED;
        this.updatedAt = now;
    }

    public void pointTo(UUID estimateId) {
        this.currentEstimateId = estimateId;
    }

    private void write(PlanConditions conditions) {
        PlanSchedule schedule = conditions.schedule();
        this.regionId = conditions.regionId();
        this.guestChoice = conditions.guestChoice();
        this.datePrecision = schedule.datePrecision();
        this.weddingDate = schedule.weddingDate();
        this.weddingMonth = schedule.weddingMonth();
        this.assumptionDate = schedule.assumptionDate();
        this.effectiveWeddingDate = schedule.effectiveWeddingDate();
        this.budgetAmount = conditions.budgetAmount();
        this.preferredMoods = conditions.preferredMoods().toArray(String[]::new);
        this.selectedBandId = conditions.selectedBandId();
    }

    public PlanSchedule schedule() {
        return new PlanSchedule(datePrecision, weddingDate, weddingMonth, assumptionDate, effectiveWeddingDate);
    }

    public UUID getId() {
        return id;
    }

    public UUID getOwnerUserId() {
        return ownerUserId;
    }

    public Status getStatus() {
        return status;
    }

    public Short getRegionId() {
        return regionId;
    }

    public GuestChoice getGuestChoice() {
        return guestChoice;
    }

    public BigDecimal getBudgetAmount() {
        return budgetAmount;
    }

    public List<String> getPreferredMoods() {
        return List.of(preferredMoods);
    }

    public Long getSelectedBandId() {
        return selectedBandId;
    }

    public UUID getCurrentEstimateId() {
        return currentEstimateId;
    }

    public OffsetDateTime getConditionsUpdatedAt() {
        return conditionsUpdatedAt;
    }

    public OffsetDateTime getScheduleUpdatedAt() {
        return scheduleUpdatedAt;
    }

    public UUID getUpdatedBy() {
        return updatedBy;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
