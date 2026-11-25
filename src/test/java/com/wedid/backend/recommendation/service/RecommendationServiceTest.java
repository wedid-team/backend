
package com.wedid.backend.recommendation.service;

import com.wedid.backend.vendor.entity.HallPriceDetail;
import com.wedid.backend.vendor.entity.PriceRecord;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.wedid.backend.recommendation.repository.RecommendationRepository;
import com.wedid.backend.vendor.repository.PriceRecordRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

class RecommendationServiceTest {

    private final RecommendationService service =
            new RecommendationService(
                    mock(RecommendationRepository.class),
                    mock(PriceRecordRepository.class),
                    new HallPriceSelector(),
                    new RecommendationScoreCalculator()
            );

    @Test
    void calculatePriceWithGuaranteedGuests() {
        HallPriceDetail detail = mock(HallPriceDetail.class);
        PriceRecord record = mock(PriceRecord.class);

        when(detail.getGuaranteedGuests()).thenReturn(200);
        when(detail.getRentalMin()).thenReturn(new BigDecimal("5000000"));
        when(detail.getRentalMax()).thenReturn(new BigDecimal("5000000"));
        when(detail.getMealMin()).thenReturn(new BigDecimal("70000"));
        when(detail.getMealMax()).thenReturn(new BigDecimal("70000"));
        when(detail.getDecorationInRental()).thenReturn(true);
        when(detail.getOtherRequiredFee()).thenReturn(new BigDecimal("1000000"));
        when(record.getVatStatus()).thenReturn("INCLUDED");

        // 첫 번째 테스트
        var result = service.calculateHallPrice(
                detail, record, 150, 150
        );

        assertEquals(200, result.minBilledGuests());
        assertEquals(200, result.maxBilledGuests());
        assertEquals(0, new BigDecimal("20000000")
                .compareTo(result.minPrice()));
        assertEquals(0, new BigDecimal("20000000")
                .compareTo(result.maxPrice()));
    }

    @Test
    void calculatePriceWithDecorationAndVat() {
        HallPriceDetail detail = mock(HallPriceDetail.class);
        PriceRecord record = mock(PriceRecord.class);

        when(detail.getGuaranteedGuests()).thenReturn(100);
        when(detail.getRentalMin()).thenReturn(new BigDecimal("3000000"));
        when(detail.getRentalMax()).thenReturn(new BigDecimal("3000000"));
        when(detail.getMealMin()).thenReturn(new BigDecimal("60000"));
        when(detail.getMealMax()).thenReturn(new BigDecimal("60000"));
        when(detail.getDecorationMin()).thenReturn(new BigDecimal("1000000"));
        when(detail.getDecorationMax()).thenReturn(new BigDecimal("1000000"));
        when(detail.getDecorationInRental()).thenReturn(false);
        when(record.getVatStatus()).thenReturn("EXCLUDED");

        // 두 번째 테스트
        var result = service.calculateHallPrice(
                detail, record, 150, 150
        );

        // (3,000,000 + 60,000 * 150 + 1,000,000) * 1.1
        assertEquals(0, new BigDecimal("14300000")
                .compareTo(result.minPrice()));
        assertEquals(0, new BigDecimal("14300000")
                .compareTo(result.maxPrice()));
    }
    @Test
    void calculateHallPrice_withGuestRange() {
        HallPriceDetail detail = mock(HallPriceDetail.class);
        PriceRecord record = mock(PriceRecord.class);

        when(detail.getGuaranteedGuests()).thenReturn(130);
        when(detail.getRentalMin())
                .thenReturn(new BigDecimal("3000000"));
        when(detail.getRentalMax())
                .thenReturn(new BigDecimal("3000000"));
        when(detail.getMealMin())
                .thenReturn(new BigDecimal("70000"));
        when(detail.getMealMax())
                .thenReturn(new BigDecimal("70000"));
        when(detail.getDecorationInRental()).thenReturn(true);
        when(record.getVatStatus()).thenReturn("INCLUDED");

        var result = service.calculateHallPrice(
                detail, record, 100, 150
        );

        assertEquals(130, result.minBilledGuests());
        assertEquals(150, result.maxBilledGuests());

        assertEquals(
                0,
                new BigDecimal("12100000")
                        .compareTo(result.minPrice())
        );

        assertEquals(
                0,
                new BigDecimal("13500000")
                        .compareTo(result.maxPrice())
        );
    }
    @Test
    void useLatestPublishedPriceWhenNoDateMatch() {
        RecommendationRepository recommendationRepository =
                mock(RecommendationRepository.class);
        PriceRecordRepository priceRecordRepository =
                mock(PriceRecordRepository.class);

        RecommendationService recommendationService =
                new RecommendationService(
                        recommendationRepository,
                        priceRecordRepository,
                        new HallPriceSelector(),
                        new RecommendationScoreCalculator()
                );

        HallPriceDetail detail = mock(HallPriceDetail.class);
        PriceRecord record = mock(PriceRecord.class);

        when(detail.getPriceRecordId()).thenReturn(101L);
        when(detail.getApplicableYear()).thenReturn(2027);
        when(detail.getWeekdayStandard()).thenReturn("전체");
        when(detail.getTimeStandard()).thenReturn("전체");
        when(detail.getSeasonStandard()).thenReturn("전체");

        when(detail.getGuaranteedGuests()).thenReturn(100);
        when(detail.getRentalMin()).thenReturn(new BigDecimal("3000000"));
        when(detail.getRentalMax()).thenReturn(new BigDecimal("3000000"));
        when(detail.getMealMin()).thenReturn(new BigDecimal("70000"));
        when(detail.getMealMax()).thenReturn(new BigDecimal("70000"));
        when(detail.getDecorationInRental()).thenReturn(true);

        when(record.getHallId()).thenReturn(1L);
        when(record.getPublishedAt()).thenReturn(
                LocalDate.of(2027, 1, 10)
        );
        when(record.getVatStatus()).thenReturn("INCLUDED");

        when(recommendationRepository.findEligibleHallPrices("SEOUL_GANGNAM", 150))
                .thenReturn(List.of(detail));

        when(priceRecordRepository.findById(101L))
                .thenReturn(Optional.of(record));

        var result = recommendationService.findHallPrices(
                "SEOUL_GANGNAM",
                100,
                150,
                LocalDate.of(2028, 3, 18)
        );

        assertEquals(1, result.size());
        assertEquals(1L, result.get(0).hallId());
        assertTrue(result.get(0).fallbackApplied());
        assertEquals(100, result.get(0).guaranteedGuests());
    }
    @Test
    void fallbackShouldNotUseSaturdayPriceForSundayWedding() {
        RecommendationRepository recommendationRepository =
                mock(RecommendationRepository.class);
        PriceRecordRepository priceRecordRepository =
                mock(PriceRecordRepository.class);

        RecommendationService recommendationService =
                new RecommendationService(
                        recommendationRepository,
                        priceRecordRepository,
                        new HallPriceSelector(),
                        new RecommendationScoreCalculator()
                );

        HallPriceDetail detail = mock(HallPriceDetail.class);
        PriceRecord record = mock(PriceRecord.class);

        when(detail.getPriceRecordId()).thenReturn(101L);
        when(detail.getApplicableYear()).thenReturn(2027);
        when(detail.getWeekdayStandard()).thenReturn("토요일");
        when(detail.getTimeStandard()).thenReturn("점심");
        when(detail.getSeasonStandard()).thenReturn("전체");

        when(record.getHallId()).thenReturn(1L);
        when(record.getPublishedAt())
                .thenReturn(LocalDate.of(2027, 1, 10));

        when(recommendationRepository.findEligibleHallPrices(
                "SEOUL_GANGNAM", 150
        )).thenReturn(List.of(detail));

        when(priceRecordRepository.findById(101L))
                .thenReturn(Optional.of(record));
        when(detail.getRentalMin())
                .thenReturn(BigDecimal.valueOf(3_000_000));

        when(detail.getRentalMax())
                .thenReturn(BigDecimal.valueOf(3_000_000));

        when(detail.getMealMin())
                .thenReturn(BigDecimal.valueOf(70_000));

        when(detail.getMealMax())
                .thenReturn(BigDecimal.valueOf(70_000));
        when(detail.getGuaranteedGuests()).thenReturn(100);
        when(detail.getDecorationInRental()).thenReturn(true);
        when(record.getVatStatus()).thenReturn("INCLUDED");

        var result = recommendationService.findHallPrices(
                "SEOUL_GANGNAM",
                100,
                150,
                LocalDate.of(2028, 3, 19) // 일요일
        );

        assertTrue(result.isEmpty());
    }
    @Test
    void fallbackShouldExcludePriceOutsideApplicableMonths() {
        RecommendationRepository repository =
                mock(RecommendationRepository.class);
        PriceRecordRepository priceRepository =
                mock(PriceRecordRepository.class);

        RecommendationService recommendationService =
                new RecommendationService(
                        repository,
                        priceRepository,
                        new HallPriceSelector(),
                        new RecommendationScoreCalculator()
                );

        HallPriceDetail detail = mock(HallPriceDetail.class);
        PriceRecord record = mock(PriceRecord.class);

        when(detail.getPriceRecordId()).thenReturn(101L);
        when(detail.getApplicableYear()).thenReturn(2027);
        when(detail.getWeekdayStandard()).thenReturn("전체");
        when(detail.getTimeStandard()).thenReturn("전체");
        when(detail.getSeasonStandard()).thenReturn("기간");

        // 6~8월에만 적용되는 가격
        when(detail.getStartMonth()).thenReturn((short) 6);
        when(detail.getEndMonth()).thenReturn((short) 8);

        when(record.getHallId()).thenReturn(1L);
        when(record.getPublishedAt())
                .thenReturn(LocalDate.of(2027, 1, 10));

        when(repository.findEligibleHallPrices("SEOUL_GANGNAM", 150))
                .thenReturn(List.of(detail));

        when(priceRepository.findById(101L))
                .thenReturn(Optional.of(record));

        // 2028년 3월 예식
        var result = recommendationService.findHallPrices(
                "SEOUL_GANGNAM",
                100,
                150,
                LocalDate.of(2028, 3, 18)
        );

        assertTrue(result.isEmpty());
    }
    @Test
    void fallbackShouldSelectLatestPublishedPrice() {
        RecommendationRepository repository =
                mock(RecommendationRepository.class);
        PriceRecordRepository priceRepository =
                mock(PriceRecordRepository.class);

        RecommendationService recommendationService =
                new RecommendationService(
                        repository,
                        priceRepository,
                        new HallPriceSelector(),
                        new RecommendationScoreCalculator()
                );

        HallPriceDetail oldDetail = mock(HallPriceDetail.class);
        HallPriceDetail newDetail = mock(HallPriceDetail.class);

        PriceRecord oldRecord = mock(PriceRecord.class);
        PriceRecord newRecord = mock(PriceRecord.class);

        // 동일한 예식장의 가격 기록 2개
        when(oldDetail.getPriceRecordId()).thenReturn(101L);
        when(newDetail.getPriceRecordId()).thenReturn(102L);

        when(oldDetail.getApplicableYear()).thenReturn(2027);
        when(newDetail.getApplicableYear()).thenReturn(2027);

        for (HallPriceDetail detail : List.of(oldDetail, newDetail)) {
            when(detail.getWeekdayStandard()).thenReturn("전체");
            when(detail.getTimeStandard()).thenReturn("전체");
            when(detail.getSeasonStandard()).thenReturn("전체");

            when(detail.getGuaranteedGuests()).thenReturn(100);
            when(detail.getRentalMin())
                    .thenReturn(new BigDecimal("3000000"));
            when(detail.getRentalMax())
                    .thenReturn(new BigDecimal("3000000"));
            when(detail.getMealMin())
                    .thenReturn(new BigDecimal("70000"));
            when(detail.getMealMax())
                    .thenReturn(new BigDecimal("70000"));
            when(detail.getDecorationInRental()).thenReturn(true);
        }

        // 최신 가격만 대관료를 500만 원으로 설정
        when(newDetail.getRentalMin())
                .thenReturn(new BigDecimal("5000000"));
        when(newDetail.getRentalMax())
                .thenReturn(new BigDecimal("5000000"));

        when(oldRecord.getHallId()).thenReturn(1L);
        when(newRecord.getHallId()).thenReturn(1L);

        when(oldRecord.getPublishedAt())
                .thenReturn(LocalDate.of(2027, 1, 10));
        when(newRecord.getPublishedAt())
                .thenReturn(LocalDate.of(2027, 6, 10));

        when(oldRecord.getVatStatus()).thenReturn("INCLUDED");
        when(newRecord.getVatStatus()).thenReturn("INCLUDED");

        when(repository.findEligibleHallPrices("SEOUL_GANGNAM", 150))
                .thenReturn(List.of(oldDetail, newDetail));

        when(priceRepository.findById(101L))
                .thenReturn(Optional.of(oldRecord));
        when(priceRepository.findById(102L))
                .thenReturn(Optional.of(newRecord));

        var result = recommendationService.findHallPrices(
                "SEOUL_GANGNAM",
                100,
                150,
                LocalDate.of(2028, 3, 18)
        );

        assertEquals(1, result.size());
        assertTrue(result.get(0).fallbackApplied());

        // 최신 가격: 대관료 500만 + 식대 7만 × 100~150명
        assertEquals(
                0,
                new BigDecimal("12000000")
                        .compareTo(result.get(0).minPrice())
        );
        assertEquals(
                0,
                new BigDecimal("15500000")
                        .compareTo(result.get(0).maxPrice())
        );
    }
    @Test
    void calculateHallPriceShouldRejectMissingMealPrice() {
        HallPriceDetail detail = mock(HallPriceDetail.class);
        PriceRecord record = mock(PriceRecord.class);

        when(detail.getGuaranteedGuests()).thenReturn(100);
        when(detail.getRentalMin())
                .thenReturn(new BigDecimal("3000000"));
        when(detail.getRentalMax())
                .thenReturn(new BigDecimal("3000000"));

        // 식대가 없는 상황
        when(detail.getMealMin()).thenReturn(null);

        assertThrows(
                IllegalArgumentException.class,
                () -> service.calculateHallPrice(
                        detail, record, 100, 150
                )
        );
    }
    @Test
    void calculateHallBudgetScoreWithSdmReference() {
        RecommendationService.HallPriceResult hall =
                new RecommendationService.HallPriceResult(
                        1L,
                        BigDecimal.valueOf(20_000_000),
                        BigDecimal.valueOf(25_000_000),
                        150,
                        150,
                        false
                );

        // 웨딩홀 2,000만~2,500만 원
        // 스드메 기준 302만~478만 원
        // 총 예상 비용 2,302만~2,978만 원
        Double score = service.calculateHallBudgetScore(
                hall,
                30_000_000L
        );

        // 총 예상 비용 상한이 예산 이하이므로 40점
        assertEquals(40.0, score);
    }
    @Test
    void calculateHallSizeScoreWithGuaranteedGuests() {
        RecommendationService.HallPriceResult hall =
                new RecommendationService.HallPriceResult(
                        1L,
                        BigDecimal.valueOf(20_000_000),
                        BigDecimal.valueOf(25_000_000),
                        130,
                        150,
                        false,
                        130
                );

        Double score = service.calculateHallSizeScore(
                hall,
                100,
                150
        );

        // 하객 중앙값 125명, 보증 인원 130명
        // 10 × 125 / 130 = 약 9.6154점
        assertEquals(10.0 * 125 / 130, score, 0.0001);
    }
    @Test
    void calculateHallPriceScoreWithThreeCandidates() {
        var hallA = new RecommendationService.HallPriceResult(
                1L,
                BigDecimal.valueOf(15_000_000),
                BigDecimal.valueOf(15_000_000),
                150, 150, false
        );

        var hallB = new RecommendationService.HallPriceResult(
                2L,
                BigDecimal.valueOf(20_000_000),
                BigDecimal.valueOf(20_000_000),
                150, 150, false
        );

        var hallC = new RecommendationService.HallPriceResult(
                3L,
                BigDecimal.valueOf(25_000_000),
                BigDecimal.valueOf(25_000_000),
                150, 150, false
        );

        var candidates = List.of(hallA, hallB, hallC);

        assertEquals(
                25.0,
                service.calculateHallPriceScore(hallA, candidates),
                0.0001
        );

        assertEquals(
                12.5,
                service.calculateHallPriceScore(hallB, candidates),
                0.0001
        );

        assertEquals(
                0.0,
                service.calculateHallPriceScore(hallC, candidates),
                0.0001
        );
    }
    @Test
    void calculateHallMoodScoreWithDifferentConditions() {
        // 1. 사용자와 예식장 분위기가 일치
        assertEquals(
                25,
                service.calculateHallMoodScore(
                        Set.of("화사한"),
                        Set.of("화사한", "클래식")
                )
        );

        // 2. 분위기가 일치하지 않음
        assertEquals(
                0,
                service.calculateHallMoodScore(
                        Set.of("화사한"),
                        Set.of("모던")
                )
        );

        // 3. 예식장 분위기 정보 없음
        assertEquals(
                8,
                service.calculateHallMoodScore(
                        Set.of("화사한"),
                        Set.of()
                )
        );

        // 4. 사용자가 분위기를 선택하지 않음
        assertNull(
                service.calculateHallMoodScore(
                        Set.of(),
                        Set.of("화사한")
                )
        );
    }
    @Test
    void calculateHallTotalScoreWithAllConditions() {
        var hallA = new RecommendationService.HallPriceResult(
                1L,
                BigDecimal.valueOf(15_000_000),
                BigDecimal.valueOf(15_000_000),
                150, 150, false, 100
        );

        var hallB = new RecommendationService.HallPriceResult(
                2L,
                BigDecimal.valueOf(20_000_000),
                BigDecimal.valueOf(20_000_000),
                150, 150, false, 100
        );

        var hallC = new RecommendationService.HallPriceResult(
                3L,
                BigDecimal.valueOf(25_000_000),
                BigDecimal.valueOf(25_000_000),
                150, 150, false, 100
        );

        var candidates = List.of(hallA, hallB, hallC);

        Double score = service.calculateHallTotalScore(
                hallB,
                candidates,
                30_000_000L,
                Set.of("화사한"),
                Set.of("화사한"),
                100,
                150
        );

        assertEquals(87.5, score, 0.0001);
    }
    @Test
    void rankTopFiveShouldReturnHighestScoringHalls() {
        var candidates = List.of(
                rankedHall(1L, 70.0),
                rankedHall(2L, 95.0),
                rankedHall(3L, 80.0),
                rankedHall(4L, 90.0),
                rankedHall(5L, 60.0),
                rankedHall(6L, 85.0)
        );

        var result = service.rankTopFive(candidates);

        assertEquals(5, result.size());

        assertEquals(
                List.of(2L, 4L, 6L, 3L, 1L),
                result.stream()
                        .map(r -> r.hall().hallId())
                        .toList()
        );
    }

    private RecommendationService.RankedHall rankedHall(
            Long hallId,
            double score
    ) {
        var hall = new RecommendationService.HallPriceResult(
                hallId,
                BigDecimal.valueOf(20_000_000),
                BigDecimal.valueOf(25_000_000),
                150,
                150,
                false,
                100
        );

        return new RecommendationService.RankedHall(
                hall,
                score
        );
    }
    @Test
    void recommendTopFiveShouldCalculateAndRankHalls() {
        // 1. Mock Repository 생성
        RecommendationRepository repository =
                mock(RecommendationRepository.class);

        PriceRecordRepository priceRepository =
                mock(PriceRecordRepository.class);

        RecommendationService recommendationService =
                new RecommendationService(
                        repository,
                        priceRepository,
                        new HallPriceSelector(),
                        new RecommendationScoreCalculator()
                );

        // 2. 실제 DB에서 조회한 것처럼 예식장 3개 설정
        for (long hallId = 1; hallId <= 3; hallId++) {
            HallPriceDetail detail = mock(HallPriceDetail.class);
            PriceRecord record = mock(PriceRecord.class);

            long priceRecordId = 100L + hallId;

            when(detail.getPriceRecordId())
                    .thenReturn(priceRecordId);

            when(detail.getApplicableYear()).thenReturn(2027);
            when(detail.getWeekdayStandard()).thenReturn("전체");
            when(detail.getTimeStandard()).thenReturn("전체");
            when(detail.getSeasonStandard()).thenReturn("전체");

            when(detail.getGuaranteedGuests()).thenReturn(100);

            when(detail.getRentalMin()).thenReturn(
                    BigDecimal.valueOf(hallId * 5_000_000)
            );
            when(detail.getRentalMax()).thenReturn(
                    BigDecimal.valueOf(hallId * 5_000_000)
            );

            when(detail.getMealMin()).thenReturn(
                    BigDecimal.valueOf(70_000)
            );
            when(detail.getMealMax()).thenReturn(
                    BigDecimal.valueOf(70_000)
            );

            when(detail.getDecorationInRental()).thenReturn(true);

            when(record.getHallId()).thenReturn(hallId);
            when(record.getVatStatus()).thenReturn("INCLUDED");
            when(record.getPublishedAt()).thenReturn(
                    LocalDate.of(2027, 1, 1)
            );

            when(priceRepository.findById(priceRecordId))
                    .thenReturn(Optional.of(record));

            // 각 예식장의 가격 상세정보를 모아 Repository 반환값에 등록
            if (hallId == 1) {
                when(repository.findEligibleHallPrices(
                        "SEOUL_GANGNAM", 150
                )).thenReturn(List.of(detail));
            } else {
                List<HallPriceDetail> existing =
                        repository.findEligibleHallPrices(
                                "SEOUL_GANGNAM", 150
                        );

                List<HallPriceDetail> updated =
                        new java.util.ArrayList<>(existing);

                updated.add(detail);

                when(repository.findEligibleHallPrices(
                        "SEOUL_GANGNAM", 150
                )).thenReturn(updated);
            }
        }

        // 3. 실제 추천 메서드 실행
        var result = recommendationService.recommendTopFive(
                "SEOUL_GANGNAM",
                100,
                150,
                LocalDate.of(2028, 3, 18),
                30_000_000L,
                Set.of("화사한")
        );

        // 4. 추천 결과 검증
        assertEquals(3, result.size());

        assertEquals(
                List.of(1L, 2L, 3L),
                result.stream()
                        .map(r -> r.hall().hallId())
                        .toList()
        );

        // 점수가 높은 순서인지 확인
        assertTrue(
                result.get(0).totalScore()
                        >= result.get(1).totalScore()
        );

        assertTrue(
                result.get(1).totalScore()
                        >= result.get(2).totalScore()
        );
    }
}
