package com.wedid.backend.roadmap.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;

/** 기본 6단계 로드맵 정의(db.md 6.3 · R-01). */
@Entity
@Table(name = "roadmap_step_templates", schema = "public")
public class RoadmapStepTemplate {

    /** 금액 단계 구분. VENUE · SDM만 예상 비용과 실제 계약 금액을 가진다. */
    public enum CostKind {
        NONE, VENUE, SDM
    }

    @Id
    private Short id;

    @Column(nullable = false)
    private String code;

    @Column(nullable = false)
    private String title;

    @Column(name = "sort_order", nullable = false)
    private Short sortOrder;

    @Column(name = "days_before_start", nullable = false)
    private Integer daysBeforeStart;

    @Column(name = "days_before_end", nullable = false)
    private Integer daysBeforeEnd;

    @Enumerated(EnumType.STRING)
    @Column(name = "cost_kind", nullable = false)
    private CostKind costKind;

    protected RoadmapStepTemplate() {
    }

    public LocalDate recommendedStartDate(LocalDate weddingDate) {
        return weddingDate.minusDays(daysBeforeStart);
    }

    public LocalDate recommendedEndDate(LocalDate weddingDate) {
        return weddingDate.minusDays(daysBeforeEnd);
    }

    public Short getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getTitle() {
        return title;
    }

    public Short getSortOrder() {
        return sortOrder;
    }

    public CostKind getCostKind() {
        return costKind;
    }
}
