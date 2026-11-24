package com.wedid.backend.estimate.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;

/** 계산·정렬 규칙 버전. 사용된 버전은 수정하지 않고 새 버전을 만든다(db.md 4.20). */
@Entity
@Table(name = "calculation_policies", schema = "public")
public class CalculationPolicy {

    public static final String ACTIVE = "ACTIVE";
    public static final String DRAFT = "DRAFT";

    @Id
    private Long id;

    @Column(nullable = false)
    private String version;

    @Column(nullable = false)
    private String status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private String settings;

    @Column(name = "effective_at")
    private OffsetDateTime effectiveAt;

    @Column(name = "created_at", nullable = false,
            insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected CalculationPolicy() {
    }

    public Long getId() {
        return id;
    }

    public String getVersion() {
        return version;
    }

    public String getStatus() {
        return status;
    }

    public String getSettings() {
        return settings;
    }
}
