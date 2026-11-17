package com.wedid.backend.estimate.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "market_statistics")
public class MarketStatistic {

    @Id
    private Long id;

    @Column(name = "reference_month")
    private LocalDate referenceMonth;

    @Column(name = "scope_type")
    private String scopeType;

    @Column(name = "region_id")
    private Short regionId;

    @Column(name = "region_raw")
    private String regionRaw;

    @Column(name = "category_raw")
    private String categoryRaw;

    @Column(name = "item_raw")
    private String itemRaw;

    @Column(name = "unit_raw")
    private String unitRaw;

    @Column(name = "p25")
    private BigDecimal p25;

    @Column(name = "median")
    private BigDecimal median;

    @Column(name = "p75")
    private BigDecimal p75;

    @Column(name = "sample_count")
    private Integer sampleCount;

    @Column(name = "source_url")
    private String sourceUrl;

    public Long getId() {
        return id;
    }

    public LocalDate getReferenceMonth() {
        return referenceMonth;
    }

    public String getScopeType() {
        return scopeType;
    }

    public Short getRegionId() {
        return regionId;
    }

    public String getRegionRaw() {
        return regionRaw;
    }

    public String getCategoryRaw() {
        return categoryRaw;
    }

    public String getItemRaw() {
        return itemRaw;
    }

    public String getUnitRaw() {
        return unitRaw;
    }

    public BigDecimal getP25() {
        return p25;
    }

    public BigDecimal getMedian() {
        return median;
    }

    public BigDecimal getP75() {
        return p75;
    }

    public Integer getSampleCount() {
        return sampleCount;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }
}