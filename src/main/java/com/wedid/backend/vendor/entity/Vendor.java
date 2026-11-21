
package com.wedid.backend.vendor.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "vendors", schema = "public")
public class Vendor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "source_dataset", nullable = false)
    private String sourceDataset;

    @Column(name = "source_vendor_id", nullable = false)
    private String sourceVendorId;

    @Column(name = "source_numeric_id")
    private Long sourceNumericId;

    @Column(name = "source_row_id", nullable = false)
    private Long sourceRowId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String category;

    @Column(name = "sido_raw")
    private String sidoRaw;

    @Column(name = "sigungu_raw")
    private String sigunguRaw;

    @Column(name = "dong_raw")
    private String dongRaw;

    private String address;
    private String phone;

    @Column(name = "region_id")
    private Short regionId;

    @Column(name = "region_raw")
    private String regionRaw;

    @Column(name = "official_url")
    private String officialUrl;

    @Column(name = "operating_status_raw")
    private String operatingStatusRaw;

    @Column(name = "alternate_names_raw")
    private String alternateNamesRaw;

    @Column(name = "meal_type_raw")
    private String mealTypeRaw;

    @Column(name = "parking_status_raw")
    private String parkingStatusRaw;

    @Column(name = "station_access_raw")
    private String stationAccessRaw;

    @Column(name = "concept_tags_raw")
    private String conceptTagsRaw;

    @Column(name = "mood_tags_raw")
    private String moodTagsRaw;

    @Column(name = "source_agency_count")
    private Integer sourceAgencyCount;

    @Column(name = "source_agencies_raw")
    private String sourceAgenciesRaw;

    @Column(name = "source_price_row_count")
    private Integer sourcePriceRowCount;

    @Column(name = "source_url")
    private String sourceUrl;

    @Column(name = "published_at")
    private LocalDate publishedAt;

    @Column(name = "collected_at")
    private LocalDate collectedAt;

    private String notes;

    @Column(name = "created_at", nullable = false,
            insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false,
            insertable = false, updatable = false)
    private OffsetDateTime updatedAt;

    protected Vendor() {
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

    public Short getRegionId() {
        return regionId;
    }

    public String getOperatingStatusRaw() {
        return operatingStatusRaw;
    }

    public String getAddress() {
        return address;
    }

    public String getOfficialUrl() {
        return officialUrl;
    }

    public String getPhone() {
        return phone;
    }

    public String getSigunguRaw() {
        return sigunguRaw;
    }

    public String getMoodTagsRaw() {
        return moodTagsRaw;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    public LocalDate getPublishedAt() {
        return publishedAt;
    }

    public LocalDate getCollectedAt() {
        return collectedAt;
    }

}
