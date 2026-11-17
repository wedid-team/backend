
package com.wedid.backend.estimate.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "products")
public class Product {

    @Id
    @Column(name = "id")
    private Long id;

    @Column(name = "product_kind")
    private String productKind;

    @Column(name = "source_dataset")
    private String sourceDataset;

    @Column(name = "source_product_key")
    private String sourceProductKey;

    @Column(name = "source_row_id")
    private Long sourceRowId;

    @Column(name = "vendor_id")
    private Long vendorId;

    @Column(name = "seller_agency_id")
    private Long sellerAgencyId;

    @Column(name = "name")
    private String name;

    @Column(name = "category")
    private String category;

    @Column(name = "region_id")
    private Short regionId;

    @Column(name = "region_raw")
    private String regionRaw;

    @Column(name = "product_type_raw")
    private String productTypeRaw;

    @Column(name = "product_type_standard")
    private String productTypeStandard;

    @Column(name = "composition_raw")
    private String compositionRaw;

    @Column(name = "session_count")
    private Integer sessionCount;

    @Column(name = "branch_line_raw")
    private String branchLineRaw;

    @Column(name = "staff_grade_raw")
    private String staffGradeRaw;

    @Column(name = "staff_grade_standard")
    private String staffGradeStandard;

    @Column(name = "staff_rank")
    private Integer staffRank;

    @Column(name = "includes_studio")
    private Boolean includesStudio;

    @Column(name = "includes_dress")
    private Boolean includesDress;

    @Column(name = "includes_makeup")
    private Boolean includesMakeup;

    @Column(name = "shooting_composition_raw")
    private String shootingCompositionRaw;

    @Column(name = "ceremony_composition_raw")
    private String ceremonyCompositionRaw;

    @Column(name = "choice_composition_raw")
    private String choiceCompositionRaw;

    @Column(name = "extra_costs_raw")
    private String extraCostsRaw;

    @Column(name = "domain_raw")
    private String domainRaw;

    @Column(name = "cost_classification_raw")
    private String costClassificationRaw;

    @Column(name = "notes")
    private String notes;

    protected Product() {
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getCategory() {
        return category;
    }

    public String getProductKind() {
        return productKind;
    }

    public Short getRegionId() {
        return regionId;
    }

    public Boolean getIncludesStudio() {
        return includesStudio;
    }

    public Boolean getIncludesDress() {
        return includesDress;
    }

    public Boolean getIncludesMakeup() {
        return includesMakeup;
    }
}
