package com.wedid.backend.estimate.entity;

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
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 계산 결과의 고정 기록. 한 번 저장하면 바꾸지 않는다(db.md 4.25).
 * 조건이 바뀌면 새 기록을 만들고 plans.current_estimate_id만 옮긴다.
 */
@Entity
@Table(name = "estimate_runs", schema = "public")
public class EstimateRun extends AssignedIdEntity {

    public enum CalculationStatus {
        COMPLETE, PARTIAL, UNAVAILABLE
    }

    @Id
    private UUID id;

    @Column(name = "plan_id", updatable = false)
    private UUID planId;

    @Column(name = "created_by", updatable = false)
    private UUID createdBy;

    @Column(name = "policy_id", nullable = false, updatable = false)
    private Long policyId;

    @Column(name = "band_id", nullable = false, updatable = false)
    private Long bandId;

    @Column(name = "region_id", nullable = false, updatable = false)
    private Short regionId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "input_snapshot", nullable = false, updatable = false)
    private String inputSnapshot;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "result_snapshot", nullable = false, updatable = false)
    private String resultSnapshot;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "source_snapshot", nullable = false, updatable = false)
    private String sourceSnapshot;

    @Column(name = "venue_min", updatable = false)
    private BigDecimal venueMin;

    @Column(name = "venue_max", updatable = false)
    private BigDecimal venueMax;

    @Column(name = "sdm_min", updatable = false)
    private BigDecimal sdmMin;

    @Column(name = "sdm_max", updatable = false)
    private BigDecimal sdmMax;

    @Column(name = "total_min", updatable = false)
    private BigDecimal totalMin;

    @Column(name = "total_max", updatable = false)
    private BigDecimal totalMax;

    @Enumerated(EnumType.STRING)
    @Column(name = "calculation_status", nullable = false, updatable = false)
    private CalculationStatus calculationStatus;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected EstimateRun() {
    }

    public EstimateRun(
            UUID planId,
            UUID createdBy,
            Long policyId,
            Long bandId,
            Short regionId,
            String inputSnapshot,
            String resultSnapshot,
            String sourceSnapshot,
            Range venue,
            Range sdm,
            Range total,
            CalculationStatus calculationStatus,
            OffsetDateTime createdAt
    ) {
        this.id = UUID.randomUUID();
        this.planId = planId;
        this.createdBy = createdBy;
        this.policyId = policyId;
        this.bandId = bandId;
        this.regionId = regionId;
        this.inputSnapshot = inputSnapshot;
        this.resultSnapshot = resultSnapshot;
        this.sourceSnapshot = sourceSnapshot;
        this.venueMin = venue.min();
        this.venueMax = venue.max();
        this.sdmMin = sdm.min();
        this.sdmMax = sdm.max();
        this.totalMin = total.min();
        this.totalMax = total.max();
        this.calculationStatus = calculationStatus;
        this.createdAt = createdAt;
    }

    /** 금액 범위. 계산할 수 없거나 미정이면 두 값 모두 null. */
    public record Range(BigDecimal min, BigDecimal max) {
        public static final Range UNKNOWN = new Range(null, null);

        public boolean isKnown() {
            return min != null && max != null;
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getPlanId() {
        return planId;
    }

    public Long getPolicyId() {
        return policyId;
    }

    public Long getBandId() {
        return bandId;
    }

    public String getResultSnapshot() {
        return resultSnapshot;
    }

    public Range getVenue() {
        return new Range(venueMin, venueMax);
    }

    public Range getSdm() {
        return new Range(sdmMin, sdmMax);
    }

    public Range getTotal() {
        return new Range(totalMin, totalMax);
    }

    public CalculationStatus getCalculationStatus() {
        return calculationStatus;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
