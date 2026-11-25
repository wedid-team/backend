
package com.wedid.backend.recommendation.service;

import com.wedid.backend.vendor.entity.HallPriceDetail;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertSame;

class HallPriceSelectorTest {

    private final HallPriceSelector selector =
            new HallPriceSelector();


    private HallPriceDetail price(
            long id, Integer year, Short start, Short end
    ) {
        HallPriceDetail detail = mock(HallPriceDetail.class);

        when(detail.getPriceRecordId()).thenReturn(id);
        when(detail.getApplicableYear()).thenReturn(year);
        when(detail.getStartMonth()).thenReturn(start);
        when(detail.getEndMonth()).thenReturn(end);

        // 실제 DB의 일반 가격 조건
        when(detail.getWeekdayStandard()).thenReturn("전체");
        when(detail.getTimeStandard()).thenReturn("전체");
        when(detail.getSeasonStandard()).thenReturn("전체");

        return detail;
    }


    @Test
    void selectFirstSeason() {
        var first = price(14L, 2027, (short) 1, (short) 8);
        var second = price(58L, 2027, (short) 9, (short) 12);

        var result = selector.select(
                List.of(first, second),
                LocalDate.of(2027, 3, 1)
        );

        assertEquals(1, result.size());
        assertEquals(14L, result.get(0).getPriceRecordId());
    }

    @Test
    void selectSecondSeason() {
        var first = price(14L, 2027, (short) 1, (short) 8);
        var second = price(58L, 2027, (short) 9, (short) 12);

        var result = selector.select(
                List.of(first, second),
                LocalDate.of(2027, 10, 1)
        );

        assertEquals(1, result.size());
        assertEquals(58L, result.get(0).getPriceRecordId());
    }

    @Test
    void selectPricesWhenDateUndecided() {

        // 현재 날짜를 2026년 11월 18일로 고정
        Clock fixedClock = Clock.fixed(
                Instant.parse("2026-11-18T00:00:00Z"),
                ZoneId.of("Asia/Seoul")
        );

        HallPriceSelector fixedSelector =
                new HallPriceSelector(fixedClock);

        // 2027년과 2028년 가격 데이터 생성
        HallPriceDetail detail2027 =
                price(101L, 2027, (short) 1, (short) 12);

        HallPriceDetail detail2028 =
                price(102L, 2028, (short) 1, (short) 12);

        List<HallPriceDetail> result = fixedSelector.select(
                List.of(detail2027, detail2028),
                null
        );

        // 2027년 가격만 선택되어야 함
        assertEquals(1, result.size());
        assertEquals(101L, result.get(0).getPriceRecordId());
    }
}
