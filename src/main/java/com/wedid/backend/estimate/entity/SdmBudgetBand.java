package com.wedid.backend.estimate.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/**
 * 정책 버전별 스드메 예산대. 경계(Q-05)가 확정되기 전에는 금액이 NULL이다.
 * 금액이 없는 예산대도 계획의 선택값으로는 저장할 수 있다.
 */
@Entity
@Table(name = "sdm_budget_bands", schema = "public")
public class SdmBudgetBand {

    @Id
    private Long id;

    @Column(name = "policy_id", nullable = false)
    private Long policyId;

    @Column(nullable = false)
    private String code;

    @Column(nullable = false)
    private String label;

    @Column(name = "lower_amount")
    private BigDecimal lowerAmount;

    @Column(name = "upper_amount")
    private BigDecimal upperAmount;

    @Column(name = "lower_inclusive", nullable = false)
    private boolean lowerInclusive;

    @Column(name = "upper_inclusive", nullable = false)
    private boolean upperInclusive;

    @Column(name = "sort_order", nullable = false)
    private Short sortOrder;

    protected SdmBudgetBand() {
    }

    public Long getId() {
        return id;
    }

    public Long getPolicyId() {
        return policyId;
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public BigDecimal getLowerAmount() {
        return lowerAmount;
    }

    public BigDecimal getUpperAmount() {
        return upperAmount;
    }
}
