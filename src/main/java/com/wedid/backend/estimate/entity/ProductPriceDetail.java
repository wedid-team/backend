
package com.wedid.backend.estimate.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "product_price_details")
public class ProductPriceDetail {

    @Id
    @Column(name = "price_record_id")
    private Long priceRecordId;

    @Column(name = "amount_min")
    private BigDecimal amountMin;

    @Column(name = "amount_max")
    private BigDecimal amountMax;

    @Column(name = "time_raw")
    private String timeRaw;

    @Column(name = "season_standard")
    private String seasonStandard;

    @Column(name = "time_standard")
    private String timeStandard;

    protected ProductPriceDetail() {
    }

    public Long getPriceRecordId() {
        return priceRecordId;
    }

    public BigDecimal getAmountMin() {
        return amountMin;
    }

    public BigDecimal getAmountMax() {
        return amountMax;
    }

    public String getSeasonStandard() {
        return seasonStandard;
    }

    public String getTimeStandard() {
        return timeStandard;
    }
}
