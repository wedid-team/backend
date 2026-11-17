
package com.wedid.backend.estimate.service;

import com.wedid.backend.vendor.repository.VendorRepository;
import com.wedid.backend.vendor.repository.HallRepository;
import com.wedid.backend.vendor.repository.PriceRecordRepository;
import com.wedid.backend.vendor.repository.HallPriceDetailRepository;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

import java.util.List;

import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import com.wedid.backend.estimate.region.RegionRepository;
import com.wedid.backend.estimate.repository.MarketStatisticsRepository;

import com.wedid.backend.estimate.repository.ProductRepository;
import com.wedid.backend.estimate.repository.ProductPriceDetailRepository;

class EstimateServiceTest {

    private final EstimateService estimateService =
            new EstimateService(
                    mock(VendorRepository.class),
                    mock(HallRepository.class),
                    mock(PriceRecordRepository.class),
                    mock(HallPriceDetailRepository.class),
                    mock(RegionRepository.class),
                    mock(WeddingDateResolver.class),
                    mock(MarketStatisticsRepository.class),
                    mock(ProductRepository.class),
                    mock(ProductPriceDetailRepository.class)
            );


    @Test
    void calculateHallCostWithGuaranteedGuests() {

        BigDecimal result = estimateService.calculateHallCost(
                new BigDecimal("7500000"),
                BigDecimal.ZERO,
                true,
                new BigDecimal("85000"),
                150,
                300,
                BigDecimal.ZERO
        );

        assertEquals(
                new BigDecimal("33000000"),
                result
        );
    }

    @Test
    void calculateHallCostWithSeparateDecoration() {

        BigDecimal result = estimateService.calculateHallCost(
                new BigDecimal("3000000"),
                new BigDecimal("3000000"),
                false,
                new BigDecimal("80000"),
                150,
                150,
                BigDecimal.ZERO
        );

        assertEquals(
                new BigDecimal("18000000"),
                result
        );
    }

    @Test
    void applyVatIncluded() {

        BigDecimal result = estimateService.applyVat(
                new BigDecimal("33000000"),
                "INCLUDED"
        );

        assertEquals(
                new BigDecimal("33000000"),
                result
        );
    }

    @Test
    void applyVatExcluded() {

        BigDecimal result = estimateService.applyVat(
                new BigDecimal("33000000"),
                "EXCLUDED"
        );

        assertEquals(
                0,
                new BigDecimal("36300000").compareTo(result)
        );
    }

    @Test
    void applyVatUnknown() {

        BigDecimal result = estimateService.applyVat(
                new BigDecimal("33000000"),
                "UNKNOWN"
        );

        assertEquals(
                new BigDecimal("33000000"),
                result
        );
    }

    @Test
    void calculatePercentileCorrectly() {

        List<BigDecimal> prices = List.of(
                new BigDecimal("10000000"),
                new BigDecimal("20000000"),
                new BigDecimal("30000000"),
                new BigDecimal("40000000"),
                new BigDecimal("50000000")
        );

        BigDecimal q25 = estimateService.calculatePercentile(
                prices, 0.25
        );

        BigDecimal q75 = estimateService.calculatePercentile(
                prices, 0.75
        );

        assertEquals(
                0,
                new BigDecimal("20000000").compareTo(q25)
        );

        assertEquals(
                0,
                new BigDecimal("40000000").compareTo(q75)
        );
    }

    @Test
    void matchesDateConditionCorrectly() {

        LocalDate weddingDate = LocalDate.of(2027, 10, 16);

        // 아르베웨딩홀: 2027년 9~12월 → 일치
        assertTrue(estimateService.matchesDateCondition(
                2027, 9, 12, weddingDate
        ));

        // 아르베웨딩홀: 2027년 1~8월 → 불일치
        assertFalse(estimateService.matchesDateCondition(
                2027, 1, 8, weddingDate
        ));

        // 빌라드지디: 7~12월 → 일치
        assertTrue(estimateService.matchesDateCondition(
                null, 7, 12, weddingDate
        ));

        // 더라움: 2027년 전체 → 일치
        assertTrue(estimateService.matchesDateCondition(
                2027, null, null, weddingDate
        ));

        // 2026년 가격 → 불일치
        assertFalse(estimateService.matchesDateCondition(
                2026, null, null, weddingDate
        ));
    }

    @Test
    void matchesDayAndTimeCorrectly() {

        // 2027년 10월 16일은 토요일
        LocalDate saturday = LocalDate.of(2027, 10, 16);
        LocalDate sunday = LocalDate.of(2027, 10, 17);

        // 토요일 점심 → 일치
        assertTrue(estimateService.matchesDayAndTime(
                "토요일", "점심", saturday
        ));

        // 주말 전체 → 일치
        assertTrue(estimateService.matchesDayAndTime(
                "주말", "전체", saturday
        ));

        // 전체 요일·전체 시간 → 일치
        assertTrue(estimateService.matchesDayAndTime(
                "전체", "전체", saturday
        ));

        // 일요일 조건 → 토요일과 불일치
        assertFalse(estimateService.matchesDayAndTime(
                "일요일", "점심", saturday
        ));

        // 평일 조건 → 토요일과 불일치
        assertFalse(estimateService.matchesDayAndTime(
                "평일", "전체", saturday
        ));

        // 토요일 조건 → 일요일과 불일치
        assertFalse(estimateService.matchesDayAndTime(
                "토요일", "점심", sunday
        ));

        // 저녁 조건 → 현재 점심 기준과 불일치
        assertFalse(estimateService.matchesDayAndTime(
                "토요일", "저녁", saturday
        ));
    }



}
