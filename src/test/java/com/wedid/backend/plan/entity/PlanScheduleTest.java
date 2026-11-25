package com.wedid.backend.plan.entity;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlanScheduleTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 24);

    @Test
    void 날짜를_정하면_그_날짜가_기준이고_계산도_그_날짜로_한다() {
        PlanSchedule schedule = PlanSchedule.resolve(
                DatePrecision.EXACT, LocalDate.of(2027, 10, 10), null, null, TODAY, null);

        assertThat(schedule.effectiveWeddingDate()).isEqualTo(LocalDate.of(2027, 10, 10));
        assertThat(schedule.calculationDate()).isEqualTo(LocalDate.of(2027, 10, 10));
        assertThat(schedule.isEstimatedDate()).isFalse();
    }

    @Test
    void 연월만_정하면_그_달_1일이_기준이고_계산은_첫_토요일로_한다() {
        PlanSchedule schedule = PlanSchedule.resolve(DatePrecision.MONTH, null, 2027, 5, TODAY, null);

        assertThat(schedule.weddingMonth()).isEqualTo(LocalDate.of(2027, 5, 1));
        assertThat(schedule.effectiveWeddingDate()).isEqualTo(LocalDate.of(2027, 5, 1));
        assertThat(schedule.calculationDate()).isEqualTo(LocalDate.of(2027, 5, 1)); // 2027-05-01은 토요일
        assertThat(schedule.isEstimatedDate()).isTrue();
    }

    @Test
    void 미정이면_기준일_12개월_뒤이고_다시_저장해도_기준일을_유지한다() {
        PlanSchedule first = PlanSchedule.resolve(DatePrecision.UNKNOWN, null, null, null, TODAY, null);
        PlanSchedule later = PlanSchedule.resolve(
                DatePrecision.UNKNOWN, null, null, null, TODAY.plusDays(30), first);

        assertThat(first.effectiveWeddingDate()).isEqualTo(LocalDate.of(2027, 11, 24));
        assertThat(later.assumptionDate()).isEqualTo(TODAY);
        assertThat(later.effectiveWeddingDate()).isEqualTo(first.effectiveWeddingDate());
        assertThat(later.differsFrom(first)).isFalse();
    }

    @Test
    void 예식_시기는_이번_달부터_2년_뒤_같은_달까지_고를_수_있다() {
        // 이번 달이면 이미 지난 날짜도 허용 (E-15 "가까운 날짜도 막지 않음")
        assertThat(exact(LocalDate.of(2026, 11, 1)).effectiveWeddingDate()).isEqualTo(LocalDate.of(2026, 11, 1));
        assertThat(exact(LocalDate.of(2028, 11, 30)).effectiveWeddingDate()).isEqualTo(LocalDate.of(2028, 11, 30));
        assertThat(month(2026, 11).weddingMonth()).isEqualTo(LocalDate.of(2026, 11, 1));
        assertThat(month(2028, 11).weddingMonth()).isEqualTo(LocalDate.of(2028, 11, 1));
    }

    @Test
    void 이번_달_이전이나_2년_뒤를_넘는_예식_시기는_거절한다() {
        assertThatThrownBy(() -> exact(LocalDate.of(2026, 10, 31)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(PlanSchedule.OUT_OF_RANGE_MESSAGE);
        assertThatThrownBy(() -> exact(LocalDate.of(2028, 12, 1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> month(2026, 10)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> month(2028, 12)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> month(Integer.MAX_VALUE, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> month(Integer.MIN_VALUE, 1)).isInstanceOf(IllegalArgumentException.class);
    }

    private static PlanSchedule exact(LocalDate date) {
        return PlanSchedule.resolve(DatePrecision.EXACT, date, null, null, TODAY, null);
    }

    private static PlanSchedule month(int year, int month) {
        return PlanSchedule.resolve(DatePrecision.MONTH, null, year, month, TODAY, null);
    }

    @Test
    void 날짜를_정했다가_미정으로_바꾸면_오늘을_새_기준일로_한다() {
        PlanSchedule exact = PlanSchedule.resolve(
                DatePrecision.EXACT, LocalDate.of(2027, 10, 10), null, null, TODAY, null);
        PlanSchedule undecided = PlanSchedule.resolve(
                DatePrecision.UNKNOWN, null, null, null, TODAY.plusDays(30), exact);

        assertThat(undecided.assumptionDate()).isEqualTo(TODAY.plusDays(30));
        assertThat(undecided.differsFrom(exact)).isTrue();
    }
}
