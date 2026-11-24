package com.wedid.backend.plan.entity;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;

/**
 * 예식 시기 입력과 그로부터 정한 기준 날짜.
 * <ul>
 *   <li>EXACT: 입력한 날짜</li>
 *   <li>MONTH: 그 달 1일 (R-08 "그 달 1일 기준 (예상)")</li>
 *   <li>UNKNOWN: 기준일(assumptionDate) + 12개월 (E-04)</li>
 * </ul>
 * 미정 날짜는 기준일을 저장해 두어, 다시 저장해도 날짜가 매일 밀리지 않게 한다(db.md 4.22).
 */
public record PlanSchedule(
        DatePrecision datePrecision,
        LocalDate weddingDate,
        LocalDate weddingMonth,
        LocalDate assumptionDate,
        LocalDate effectiveWeddingDate
) {

    static final int SELECTABLE_YEARS = 2;
    public static final String OUT_OF_RANGE_MESSAGE = "예식 시기는 이번 달부터 2년 뒤까지 선택할 수 있어요.";

    /**
     * @param previous 기존 계획의 일정. 새 계획이면 null.
     *                 기존에도 미정이었고 이번에도 미정이면 기존 기준일을 유지한다.
     */
    public static PlanSchedule resolve(
            DatePrecision precision,
            LocalDate weddingDate,
            Integer weddingYear,
            Integer weddingMonth,
            LocalDate today,
            PlanSchedule previous
    ) {
        return switch (precision) {
            case EXACT -> {
                requireSelectable(YearMonth.from(weddingDate), today);
                yield new PlanSchedule(precision, weddingDate, null, today, weddingDate);
            }
            case MONTH -> {
                // 터무니없는 연도로 YearMonth 생성이 실패(500)하지 않게 먼저 막는다
                if (Math.abs((long) weddingYear - today.getYear()) > SELECTABLE_YEARS) {
                    throw new IllegalArgumentException(OUT_OF_RANGE_MESSAGE);
                }
                requireSelectable(YearMonth.of(weddingYear, weddingMonth), today);
                LocalDate firstDay = LocalDate.of(weddingYear, weddingMonth, 1);
                yield new PlanSchedule(precision, null, firstDay, today, firstDay);
            }
            case UNKNOWN -> {
                LocalDate assumption = previous != null && previous.datePrecision() == DatePrecision.UNKNOWN
                        ? previous.assumptionDate()
                        : today;
                yield new PlanSchedule(precision, null, null, assumption, assumption.plusMonths(12));
            }
        };
    }

    /**
     * E-15 예식 시기 선택 범위: 이번 달 ~ 2년 뒤 같은 달 (양 끝 포함, 월 단위).
     * 이번 달 안이면 가까운 날짜나 이미 지난 날짜도 막지 않는다.
     */
    static void requireSelectable(YearMonth selected, LocalDate today) {
        YearMonth thisMonth = YearMonth.from(today);
        if (selected.isBefore(thisMonth) || selected.isAfter(thisMonth.plusYears(SELECTABLE_YEARS))) {
            throw new IllegalArgumentException(OUT_OF_RANGE_MESSAGE);
        }
    }

    /** 날짜가 확정되지 않아 D-day에 "(예상)"을 붙여야 하는지. */
    public boolean isEstimatedDate() {
        return datePrecision != DatePrecision.EXACT;
    }

    /**
     * 예식장 비용 계산에 쓰는 날짜. 날짜를 정했으면 그 날짜, 아니면 기준 날짜 이후 첫 토요일
     * (E-07 토요일 낮 기준, WeddingDateResolver와 같은 규칙).
     */
    public LocalDate calculationDate() {
        if (datePrecision == DatePrecision.EXACT) {
            return weddingDate;
        }
        return effectiveWeddingDate.with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY));
    }

    /** 로드맵 기간에 영향을 주는 일정이 바뀌었는지(R-07 지난 단계 확인 기준). */
    public boolean differsFrom(PlanSchedule other) {
        return datePrecision != other.datePrecision
                || !effectiveWeddingDate.equals(other.effectiveWeddingDate);
    }
}
