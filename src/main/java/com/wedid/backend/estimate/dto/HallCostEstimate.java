
package com.wedid.backend.estimate.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record HallCostEstimate(
        BigDecimal minPrice,
        BigDecimal maxPrice,
        String priceSource,
        LocalDate referenceMonth,
        String regionName,
        String sourceUrl
) {
}
