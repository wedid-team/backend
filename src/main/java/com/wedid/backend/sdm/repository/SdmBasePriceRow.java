package com.wedid.backend.sdm.repository;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 스드메 기본 상품의 공개 가격 행 하나 (가격 행 1개 = 대행사 1곳의 공개 가격). */
public record SdmBasePriceRow(
        long vendorId,
        String vendorName,
        String category,
        Integer vendorAgencyCount,
        String officialUrl,
        String agencyName,
        String sourceUrl,
        String fileReference,
        LocalDate collectedAt,
        BigDecimal amountMin,
        BigDecimal amountMax
) {
}
