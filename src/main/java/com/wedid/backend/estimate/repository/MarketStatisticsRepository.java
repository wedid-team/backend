package com.wedid.backend.estimate.repository;

import com.wedid.backend.estimate.entity.MarketStatistic;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MarketStatisticsRepository
        extends JpaRepository<MarketStatistic, Long> {

    // 1. 특정 지역의 최신 참가격 통계 조회
    Optional<MarketStatistic>
    findFirstByScopeTypeAndRegionIdAndCategoryRawAndItemRawAndUnitRawAndP25IsNotNullAndP75IsNotNullOrderByReferenceMonthDescIdDesc(
            String scopeType,
            Short regionId,
            String categoryRaw,
            String itemRaw,
            String unitRaw
    );

    // 2. 수도권 또는 전국의 최신 참가격 통계 조회
    Optional<MarketStatistic>
    findFirstByScopeTypeAndCategoryRawAndItemRawAndUnitRawAndP25IsNotNullAndP75IsNotNullOrderByReferenceMonthDescIdDesc(
            String scopeType,
            String categoryRaw,
            String itemRaw,
            String unitRaw
    );
}