
package com.wedid.backend.vendor.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "price_records", schema = "public")
public class PriceRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "source_dataset", nullable = false)
    private String sourceDataset;

    @Column(name = "source_price_id", nullable = false)
    private String sourcePriceId;

    @Column(name = "source_row_id", nullable = false)
    private Long sourceRowId;

    @Column(name = "hall_id")
    private Long hallId;

    @Column(name = "product_id")
    private Long productId;

    @Column(name = "agency_id")
    private Long agencyId;

    @Column(name = "price_type", nullable = false)
    private String priceType;

    @Column(name = "price_type_basis")
    private String priceTypeBasis;

    @Column(name = "vat_status", nullable = false)
    private String vatStatus;

    @Column(name = "source_url")
    private String sourceUrl;

    @Column(name = "source_detail_raw")
    private String sourceDetailRaw;

    @Column(name = "published_at")
    private LocalDate publishedAt;

    @Column(name = "file_reference_raw")
    private String fileReferenceRaw;

    @Column(name = "collected_at")
    private LocalDate collectedAt;

    @Column(name = "conditions_raw")
    private String conditionsRaw;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected PriceRecord() {
    }

    public Long getId() {
        return id;
    }

    public Long getHallId() {
        return hallId;
    }

    public Long getProductId() {
        return productId;
    }

    public String getSourceDataset() {
        return sourceDataset;
    }

    public String getPriceType() {
        return priceType;
    }

    public String getVatStatus() {
        return vatStatus;
    }

    public LocalDate getPublishedAt() {
        return publishedAt;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }
}
