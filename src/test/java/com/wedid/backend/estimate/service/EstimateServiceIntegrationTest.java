
package com.wedid.backend.estimate.service;

import com.wedid.backend.estimate.dto.EstimateRequest;
import com.wedid.backend.estimate.dto.HallCostEstimate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class EstimateServiceIntegrationTest {

    @Autowired
    private EstimateService estimateService;

    // 1. 실제 DB 기반 예식장 예상 비용 범위 계산
    @Test
    void calculateEstimateHallRange_withRealDatabase() {

        EstimateRequest request = new EstimateRequest(
                "SEOUL_GANGNAM",
                "FROM_150_TO_200",
                EstimateRequest.WeddingDateType.UNDECIDED,
                null,
                null,
                null,
                null,
                null
        );

        Optional<BigDecimal[]> result =
                estimateService.calculateEstimateHallRange(request);

        assertTrue(
                result.isPresent(),
                "서울 강남, 하객 150~200명 조건에서 예상 비용을 계산하지 못했습니다."
        );

        BigDecimal[] range = result.get();

        assertEquals(2, range.length);
        assertTrue(range[0].compareTo(BigDecimal.ZERO) >= 0);
        assertTrue(range[1].compareTo(range[0]) >= 0);

        System.out.println("웨딩홀 Q25 예상 비용: " + range[0]);
        System.out.println("웨딩홀 Q75 예상 비용: " + range[1]);
    }

    // 2. 실제 DB의 예식장별 비용 계산 과정 확인
    @Test
    void inspectHallCostCalculation_withRealDatabase() {

        Short regionId =
                estimateService.resolveRegionId("SEOUL_GANGNAM");

        LocalDate calculationDate =
                LocalDate.of(2027, 11, 20);

        var records = estimateService.findSelectedPriceRecords(
                regionId,
                200,
                calculationDate
        );

        System.out.println("선택된 웨딩홀 수: " + records.size());

        for (var record : records) {

            var detail =
                    estimateService.findPriceDetail(record.getId());

            if (detail.isEmpty()) {
                continue;
            }

            var d = detail.get();

            if (d.getRentalMin() == null
                    || d.getMealMin() == null
                    || d.getGuaranteedGuests() == null
                    || d.getDecorationInRental() == null
                    || (Boolean.FALSE.equals(d.getDecorationInRental())
                    && d.getDecorationMin() == null)) {

                System.out.println(
                        "필수 가격 누락 - 가격 기록 ID: "
                                + record.getId()
                );
                continue;
            }

            BigDecimal[] range =
                    estimateService.calculateHallCostRange(
                            record.getId(),
                            150,
                            200
                    );

            BigDecimal midpoint = range[0]
                    .add(range[1])
                    .divide(
                            BigDecimal.valueOf(2),
                            2,
                            RoundingMode.HALF_UP
                    );

            System.out.println(
                    "홀 ID: " + record.getHallId()
                            + " | 가격 기록 ID: " + record.getId()
                            + " | 최소: " + range[0]
                            + " | 최대: " + range[1]
                            + " | 중간값: " + midpoint
            );
        }
    }

    // 3. 예식장 가격 데이터 부족 시 참가격 통계 사용
    @Test
    void fallbackToMarketStatisticsWhenNoHallData() {

        Short regionId =
                estimateService.resolveRegionId("GYEONGGI");

        BigDecimal[] result = estimateService
                .calculateRegionalHallCostRange(
                        regionId,
                        150,
                        200,
                        LocalDate.of(2027, 11, 20)
                )
                .orElseThrow();

        assertTrue(result[0].compareTo(BigDecimal.ZERO) >= 0);
        assertTrue(result[1].compareTo(result[0]) >= 0);

        System.out.println("예상 비용 하한: " + result[0]);
        System.out.println("예상 비용 상한: " + result[1]);
    }

    // 4. 예식장 예상 비용 DTO 및 가격 출처 검증
    @Test
    void calculateHallCostEstimate_withRealDatabase() {

        EstimateRequest request = new EstimateRequest(
                "SEOUL_GANGNAM",
                "FROM_150_TO_200",
                EstimateRequest.WeddingDateType.UNDECIDED,
                null,
                null,
                null,
                null,
                null
        );

        HallCostEstimate result = estimateService
                .calculateHallCostEstimate(request)
                .orElseThrow();

        assertNotNull(result.minPrice());
        assertNotNull(result.maxPrice());
        assertNotNull(result.priceSource());

        assertTrue(
                result.minPrice().compareTo(BigDecimal.ZERO) >= 0
        );

        assertTrue(
                result.maxPrice().compareTo(result.minPrice()) >= 0
        );

        assertTrue(
                result.priceSource().equals("HALL_DATA")
                        || result.priceSource().equals("MARKET_STATISTICS")
        );

        if ("HALL_DATA".equals(result.priceSource())) {
            assertNotNull(result.sampleCount());
            assertTrue(result.sampleCount() >= 10);
        }

        System.out.println("예식장 예상 비용 하한: " + result.minPrice());
        System.out.println("예식장 예상 비용 상한: " + result.maxPrice());
        System.out.println("가격 출처 유형: " + result.priceSource());
        System.out.println("계산에 사용된 홀 수: " + result.sampleCount());
        System.out.println("기준 월: " + result.referenceMonth());
        System.out.println("출처 URL: " + result.sourceUrl());
    }
}
