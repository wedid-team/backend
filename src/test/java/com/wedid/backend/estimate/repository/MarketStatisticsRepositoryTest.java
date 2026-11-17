
package com.wedid.backend.estimate.repository;

import com.wedid.backend.estimate.entity.MarketStatistic;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class MarketStatisticsRepositoryTest {

    @Autowired
    private MarketStatisticsRepository repository;

    @Test
    void findGangnamMarketStatistic_withRealDatabase() {

        MarketStatistic statistic = repository
                .findFirstByScopeTypeAndRegionIdAndCategoryRawAndItemRawAndUnitRawAndP25IsNotNullAndP75IsNotNullOrderByReferenceMonthDescIdDesc(
                        "REGION",
                        (short) 1,
                        "예식장",
                        "총 계약금액",
                        "원"
                )
                .orElseThrow(() ->
                        new AssertionError("서울 강남 참가격 통계를 찾지 못했습니다.")
                );

        System.out.println("지역: " + statistic.getRegionRaw());
        System.out.println("기준 월: " + statistic.getReferenceMonth());
        System.out.println("Q25: " + statistic.getP25());
        System.out.println("Q75: " + statistic.getP75());

        assertEquals(
                0,
                statistic.getP25().compareTo(
                        new BigDecimal("19500000")
                )
        );

        assertEquals(
                0,
                statistic.getP75().compareTo(
                        new BigDecimal("35200000")
                )
        );
    }
}
