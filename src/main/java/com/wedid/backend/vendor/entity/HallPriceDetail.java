
package com.wedid.backend.vendor.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "hall_price_details", schema = "public")
public class HallPriceDetail {

    @Id
    @Column(name = "price_record_id")
    private Long priceRecordId;

    @Column(name = "source_numeric_price_id")
    private Long sourceNumericPriceId;

    @Column(name = "source_numeric_vendor_id")
    private Long sourceNumericVendorId;

    @Column(name = "hall_type_raw")
    private String hallTypeRaw;

    @Column(name = "is_exclusive")
    private Boolean isExclusive;

    @Column(name = "rental_min")
    private BigDecimal rentalMin;

    @Column(name = "rental_max")
    private BigDecimal rentalMax;

    @Column(name = "guaranteed_guests")
    private Integer guaranteedGuests;

    @Column(name = "meal_min")
    private BigDecimal mealMin;

    @Column(name = "meal_max")
    private BigDecimal mealMax;

    @Column(name = "decoration_min")
    private BigDecimal decorationMin;

    @Column(name = "decoration_max")
    private BigDecimal decorationMax;

    @Column(name = "decoration_in_rental")
    private Boolean decorationInRental;

    @Column(name = "capacity_min")
    private Integer capacityMin;

    @Column(name = "capacity_max")
    private Integer capacityMax;

    @Column(name = "weekday_raw")
    private String weekdayRaw;

    @Column(name = "time_raw")
    private String timeRaw;

    @Column(name = "season_raw")
    private String seasonRaw;

    @Column(name = "weekday_standard")
    private String weekdayStandard;

    @Column(name = "time_standard")
    private String timeStandard;

    @Column(name = "season_standard")
    private String seasonStandard;

    @Column(name = "start_month")
    private Short startMonth;

    @Column(name = "end_month")
    private Short endMonth;

    @Column(name = "applicable_year")
    private Integer applicableYear;

    @Column(name = "included_services_raw")
    private String includedServicesRaw;

    @Column(name = "deposit_raw")
    private String depositRaw;

    @Column(name = "penalty_raw")
    private String penaltyRaw;

    @Column(name = "beverage_in_meal")
    private Boolean beverageInMeal;

    @Column(name = "beverage_per_person")
    private BigDecimal beveragePerPerson;

    @Column(name = "extra_guest_meal_min")
    private BigDecimal extraGuestMealMin;

    @Column(name = "extra_guest_meal_max")
    private BigDecimal extraGuestMealMax;

    @Column(name = "rental_list_price")
    private BigDecimal rentalListPrice;

    @Column(name = "meal_list_price")
    private BigDecimal mealListPrice;

    @Column(name = "production_fee")
    private BigDecimal productionFee;

    @Column(name = "other_required_fee")
    private BigDecimal otherRequiredFee;

    @Column(name = "normalization_notes_raw")
    private String normalizationNotesRaw;

    protected HallPriceDetail() {
    }

    public Long getPriceRecordId() {
        return priceRecordId;
    }

    public BigDecimal getRentalMin() {
        return rentalMin;
    }

    public BigDecimal getRentalMax() {
        return rentalMax;
    }

    public Integer getGuaranteedGuests() {
        return guaranteedGuests;
    }

    public BigDecimal getMealMin() {
        return mealMin;
    }

    public BigDecimal getMealMax() {
        return mealMax;
    }

    public BigDecimal getDecorationMin() {
        return decorationMin;
    }

    public BigDecimal getDecorationMax() {
        return decorationMax;
    }


    public Boolean getDecorationInRental() {
        return decorationInRental;
    }

    public Integer getCapacityMin() {
        return capacityMin;
    }

    public Integer getCapacityMax() {
        return capacityMax;
    }


    public BigDecimal getOtherRequiredFee() {
        return otherRequiredFee;
    }

    public BigDecimal getProductionFee() {
        return productionFee;
    }

    public String getWeekdayStandard() {
        return weekdayStandard;
    }

    public String getTimeStandard() {
        return timeStandard;
    }

    public String getSeasonStandard() {
        return seasonStandard;
    }

    public Short getStartMonth() {
        return startMonth;
    }

    public Short getEndMonth() {
        return endMonth;
    }

    public Integer getApplicableYear() {
        return applicableYear;
    }

}
