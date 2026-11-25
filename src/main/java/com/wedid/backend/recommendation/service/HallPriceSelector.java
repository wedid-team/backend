
package com.wedid.backend.recommendation.service;

import com.wedid.backend.vendor.entity.HallPriceDetail;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

@Component
public class HallPriceSelector {

    private final Clock clock;

    public HallPriceSelector() {
        this.clock = Clock.systemDefaultZone();
    }

    public HallPriceSelector(Clock clock) {
        this.clock = clock;
    }

    public List<HallPriceDetail> select(
            List<HallPriceDetail> details,
            LocalDate weddingDate
    ) {
        LocalDate targetDate = weddingDate;

        if (targetDate == null) {
            targetDate = LocalDate.now(clock)
                    .plusMonths(12)
                    .with(TemporalAdjusters.nextOrSame(
                            DayOfWeek.SATURDAY
                    ));
        }

        int year = targetDate.getYear();
        int month = targetDate.getMonthValue();
        DayOfWeek dayOfWeek = targetDate.getDayOfWeek();

        // 연도·월·요일·시간·시즌 조건을 모두 확인
        List<HallPriceDetail> matches = details.stream()
                .filter(detail -> matchesYear(detail, year))
                .filter(detail -> matchesMonth(detail, month))
                .filter(detail -> matchesWeekday(detail, dayOfWeek))
                .filter(this::matchesTime)
                .filter(this::matchesSeason)
                .toList();

        // 해당 연도 가격을 연도 미지정 가격보다 우선
        List<HallPriceDetail> exactMatches = matches.stream()
                .filter(detail ->
                        detail.getApplicableYear() != null
                                && detail.getApplicableYear() == year
                )
                .toList();

        if (!exactMatches.isEmpty()) {
            return exactMatches;
        }

        return matches.stream()
                .filter(detail -> detail.getApplicableYear() == null)
                .toList();
    }

    private boolean matchesYear(HallPriceDetail detail, int year) {
        Integer applicableYear = detail.getApplicableYear();

        return applicableYear == null || applicableYear == year;
    }

    private boolean matchesMonth(HallPriceDetail detail, int month) {
        Short start = detail.getStartMonth();
        Short end = detail.getEndMonth();

        if (start == null && end == null) {
            return true;
        }

        if (start == null) {
            return month <= end;
        }

        if (end == null) {
            return month >= start;
        }

        if (start <= end) {
            return month >= start && month <= end;
        }

        // 11월~2월처럼 연도를 넘어가는 경우
        return month >= start || month <= end;
    }

    private boolean matchesWeekday(
            HallPriceDetail detail,
            DayOfWeek dayOfWeek
    ) {
        String weekday = detail.getWeekdayStandard();

        if ("전체".equals(weekday)) {
            return true;
        }

        if ("토요일".equals(weekday)) {
            return dayOfWeek == DayOfWeek.SATURDAY;
        }

        if ("주말".equals(weekday)) {
            return dayOfWeek == DayOfWeek.SATURDAY
                    || dayOfWeek == DayOfWeek.SUNDAY;
        }

        if ("일요일".equals(weekday)) {
            return dayOfWeek == DayOfWeek.SUNDAY;
        }

        return false;
    }

    private boolean matchesTime(HallPriceDetail detail) {
        String time = detail.getTimeStandard();

        // WEDID 기본 기준: 낮 예식
        return "전체".equals(time) || "점심".equals(time);
    }

    private boolean matchesSeason(HallPriceDetail detail) {
        String season = detail.getSeasonStandard();

        if ("전체".equals(season) || "기간".equals(season)) {
            return true;
        }

        // 성수기·비수기는 적용 월 정보가 없는 경우
        // 임의로 특정 월에 적용하지 않음
        if ("성수기".equals(season) || "비수기".equals(season)) {
            return detail.getStartMonth() != null
                    && detail.getEndMonth() != null;
        }

        return false;
    }
}
