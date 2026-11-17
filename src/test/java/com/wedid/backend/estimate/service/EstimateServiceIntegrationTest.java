
package com.wedid.backend.estimate.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;

import com.wedid.backend.estimate.dto.EstimateRequest;
import java.util.Optional;

import java.util.Map;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class EstimateServiceIntegrationTest {

    @Autowired
    private EstimateService estimateService;

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
    @Test
    void inspectHallCostCalculation_withRealDatabase() {
        Short regionId = estimateService.resolveRegionId("SEOUL_GANGNAM");

        LocalDate calculationDate = LocalDate.of(2027, 11, 20);

        var records = estimateService.findSelectedPriceRecords(
                regionId,
                200,
                calculationDate
        );

        System.out.println("선택된 웨딩홀 수: " + records.size());

        for (var record : records) {
            var detail = estimateService.findPriceDetail(record.getId());

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
                        "필수 가격 누락 - 가격 기록 ID: " + record.getId()
                );
                continue;
            }

            BigDecimal[] range = estimateService.calculateHallCostRange(
                    record.getId(),
                    150,
                    200
            );

            BigDecimal midpoint = range[0]
                    .add(range[1])
                    .divide(BigDecimal.valueOf(2), 2,
                            java.math.RoundingMode.HALF_UP);

            System.out.println(
                    "홀 ID: " + record.getHallId()
                            + " | 가격 기록 ID: " + record.getId()
                            + " | 최소: " + range[0]
                            + " | 최대: " + range[1]
                            + " | 중간값: " + midpoint
            );
        }
    }
    @Test
    void fallbackToMarketStatisticsWhenNoHallData() {
        Short regionId = 3; // 경기도

        BigDecimal[] result = estimateService
                .calculateRegionalHallCostRange(
                        regionId,
                        150,
                        200,
                        LocalDate.of(2027, 11, 20)
                )
                .orElseThrow();

        System.out.println("예상 비용 하한: " + result[0]);
        System.out.println("예상 비용 상한: " + result[1]);
    }
    @Test
    void calculateSdmBudgetTypesTest() {

        Map<String, BigDecimal> result =
                estimateService.calculateSdmBudgetTypes();

        System.out.println("실속형: " + result.get("BUDGET"));
        System.out.println("균형형: " + result.get("BALANCED"));
        System.out.println("프리미엄형: " + result.get("PREMIUM"));

        assertEquals(3, result.size());

        assertTrue(result.get("BUDGET")
                .compareTo(result.get("BALANCED")) <= 0);

        assertTrue(result.get("BALANCED")
                .compareTo(result.get("PREMIUM")) <= 0);
    }

    @Test
    void calculateTotalEstimateTest() {

        // 서울 강남, 하객 150~200명
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

        Map<String, BigDecimal[]> result =
                estimateService.calculateTotalEstimate(request);

        assertEquals(3, result.size());

        for (Map.Entry<String, BigDecimal[]> entry : result.entrySet()) {

            BigDecimal totalMin = entry.getValue()[0];
            BigDecimal totalMax = entry.getValue()[1];

            System.out.println(
                    entry.getKey()
                            + " 총 예상 비용: "
                            + totalMin
                            + " ~ "
                            + totalMax
            );

            assertTrue(totalMin.compareTo(BigDecimal.ZERO) >= 0);
            assertTrue(totalMax.compareTo(totalMin) >= 0);
        }

        assertTrue(
                result.get("BUDGET")[0]
                        .compareTo(result.get("BALANCED")[0]) <= 0
        );

        assertTrue(
                result.get("BALANCED")[0]
                        .compareTo(result.get("PREMIUM")[0]) <= 0
        );
    }


}
