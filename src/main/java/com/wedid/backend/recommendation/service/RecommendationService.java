
package com.wedid.backend.recommendation.service;

import com.wedid.backend.recommendation.repository.RecommendationRepository;
import com.wedid.backend.vendor.entity.HallPriceDetail;
import com.wedid.backend.vendor.entity.PriceRecord;
import com.wedid.backend.vendor.repository.PriceRecordRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.Set;

@Service
public class RecommendationService {
    // 스드메 기준 가격 (지역 미구분 임시값)
    private static final BigDecimal SDM_REF_MIN =
            BigDecimal.valueOf(3_020_000L);

    private static final BigDecimal SDM_REF_MAX =
            BigDecimal.valueOf(4_780_000L);

    private final RecommendationRepository recommendationRepository;
    private final PriceRecordRepository priceRecordRepository;
    private final HallPriceSelector hallPriceSelector;
    private final RecommendationScoreCalculator scoreCalculator;

    public RecommendationService(
            RecommendationRepository recommendationRepository,
            PriceRecordRepository priceRecordRepository,
            HallPriceSelector hallPriceSelector,
            RecommendationScoreCalculator scoreCalculator
    ) {
        this.recommendationRepository = recommendationRepository;
        this.priceRecordRepository = priceRecordRepository;
        this.hallPriceSelector = hallPriceSelector;
        this.scoreCalculator = scoreCalculator;
    }

    public record HallPriceRange(
            BigDecimal minPrice,
            BigDecimal maxPrice,
            int minBilledGuests,
            int maxBilledGuests
    ) {}

    public record HallPriceResult(
            Long hallId,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            int minBilledGuests,
            int maxBilledGuests,
            boolean fallbackApplied,
            Integer guaranteedGuests
    ) {
        // 기존 6개 인자 생성자와 호환
        public HallPriceResult(
                Long hallId,
                BigDecimal minPrice,
                BigDecimal maxPrice,
                int minBilledGuests,
                int maxBilledGuests,
                boolean fallbackApplied
        ) {
            this(
                    hallId,
                    minPrice,
                    maxPrice,
                    minBilledGuests,
                    maxBilledGuests,
                    fallbackApplied,
                    null
            );
        }
    }

    // 웨딩홀 조회 + 날짜 선택 + 비용 계산 + 중복 통합
    public List<HallPriceResult> findHallPrices(
            String regionCode,
            int minGuests,
            int maxGuests,
            LocalDate weddingDate
    ) {
        if (minGuests <= 0 || maxGuests < minGuests) {
            throw new IllegalArgumentException("Invalid guest range");
        }

        // 후보 필터링은 하객 구간 최댓값 기준
        List<HallPriceDetail> details =
                recommendationRepository.findEligibleHallPrices(
                        regionCode, maxGuests
                );

        // 웨딩홀별 가격 기록 그룹화
        Map<Long, List<HallPriceDetail>> grouped =
                details.stream()
                        .collect(Collectors.groupingBy(detail -> {
                            PriceRecord record = priceRecordRepository
                                    .findById(detail.getPriceRecordId())
                                    .orElseThrow();

                            return record.getHallId();
                        }));

        return grouped.entrySet().stream()
                .map(entry -> {
                    Long hallId = entry.getKey();

                    List<HallPriceDetail> selected =
                            hallPriceSelector.select(
                                    entry.getValue(),
                                    weddingDate
                            );

                    boolean fallbackApplied = selected.isEmpty();

                    if (fallbackApplied) {
                        selected = selectFallbackPrices(
                                entry.getValue(),
                                weddingDate
                        );
                    }

                    if (selected.isEmpty()) {
                        return null;
                    }

                    List<HallPriceRange> prices = selected.stream()
                            .map(detail -> {
                                PriceRecord record = priceRecordRepository
                                        .findById(detail.getPriceRecordId())
                                        .orElseThrow();

                                return calculateHallPrice(
                                        detail,
                                        record,
                                        minGuests,
                                        maxGuests
                                );
                            })
                            .toList();

                    BigDecimal minPrice = prices.stream()
                            .map(HallPriceRange::minPrice)
                            .min(BigDecimal::compareTo)
                            .orElseThrow();

                    BigDecimal maxPrice = prices.stream()
                            .map(HallPriceRange::maxPrice)
                            .max(BigDecimal::compareTo)
                            .orElseThrow();

                    int minBilledGuests = prices.stream()
                            .mapToInt(HallPriceRange::minBilledGuests)
                            .min()
                            .orElseThrow();

                    int maxBilledGuests = prices.stream()
                            .mapToInt(HallPriceRange::maxBilledGuests)
                            .max()
                            .orElseThrow();

                    int guaranteedGuests = selected.stream()
                            .map(HallPriceDetail::getGuaranteedGuests)
                            .filter(Objects::nonNull)
                            .mapToInt(Integer::intValue)
                            .max()
                            .orElseThrow();

                    return new HallPriceResult(
                            hallId,
                            minPrice,
                            maxPrice,
                            minBilledGuests,
                            maxBilledGuests,
                            fallbackApplied,
                            guaranteedGuests
                    );
                })
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(HallPriceResult::hallId))
                .toList();
    }
    public Double calculateHallBudgetScore(
            HallPriceResult hall,
            Long userBudget
    ) {
        // 예산 미입력 시 예산 점수 제외
        if (userBudget == null) {
            return null;
        }

        // 웨딩홀 비용 + 스드메 임시 기준 가격
        BigDecimal totalMin = hall.minPrice().add(SDM_REF_MIN);
        BigDecimal totalMax = hall.maxPrice().add(SDM_REF_MAX);

        return scoreCalculator.calculateBudgetScore(
                totalMin.longValueExact(),
                totalMax.longValueExact(),
                userBudget
        );
    }

    public Double calculateHallSizeScore(
            HallPriceResult hall,
            int minGuests,
            int maxGuests
    ) {
        if (minGuests <= 0 || maxGuests < minGuests) {
            throw new IllegalArgumentException("Invalid guest range");
        }

        if (hall.guaranteedGuests() == null) {
            return null;
        }

        return scoreCalculator.calculateSizeScore(
                minGuests,
                maxGuests,
                hall.guaranteedGuests()
        );
    }

    public Double calculateHallPriceScore(
            HallPriceResult hall,
            List<HallPriceResult> candidates
    ) {
        if (candidates == null || candidates.size() <= 1) {
            return null;
        }

        double lowestPrice = candidates.stream()
                .mapToDouble(candidate ->
                        candidate.minPrice().doubleValue() / 2.0
                                + candidate.maxPrice().doubleValue() / 2.0
                )
                .min()
                .orElseThrow();

        double highestPrice = candidates.stream()
                .mapToDouble(candidate ->
                        candidate.minPrice().doubleValue() / 2.0
                                + candidate.maxPrice().doubleValue() / 2.0
                )
                .max()
                .orElseThrow();

        return scoreCalculator.calculatePriceScore(
                hall.minPrice().longValueExact(),
                hall.maxPrice().longValueExact(),
                lowestPrice,
                highestPrice
        );
    }

    public Integer calculateHallMoodScore(
            Set<String> preferredMoods,
            Set<String> hallMoods
    ) {
        // 사용자가 분위기를 선택하지 않았다면 평가 제외
        if (preferredMoods == null || preferredMoods.isEmpty()) {
            return null;
        }

        return scoreCalculator.calculateMoodScore(
                preferredMoods,
                hallMoods
        );
    }

    public Double calculateHallTotalScore(
            HallPriceResult hall,
            List<HallPriceResult> candidates,
            Long userBudget,
            Set<String> preferredMoods,
            Set<String> hallMoods,
            int minGuests,
            int maxGuests
    ) {
        Double budgetScore = calculateHallBudgetScore(
                hall, userBudget
        );

        Integer moodScore = calculateHallMoodScore(
                preferredMoods, hallMoods
        );

        Double priceScore = calculateHallPriceScore(
                hall, candidates
        );

        Double sizeScore = calculateHallSizeScore(
                hall, minGuests, maxGuests
        );

        return scoreCalculator.calculateTotalScore(
                budgetScore,
                moodScore,
                priceScore,
                sizeScore
        );
    }

    // 하객 구간별 최소·최대 비용 계산
    public HallPriceRange calculateHallPrice(
            HallPriceDetail detail,
            PriceRecord record,
            int minGuests,
            int maxGuests
    ) {
        if (minGuests <= 0 || maxGuests < minGuests) {
            throw new IllegalArgumentException("Invalid guest range");
        }

        if (detail.getGuaranteedGuests() == null) {
            throw new IllegalArgumentException(
                    "Guaranteed guests is required"
            );
        }

        int minBilledGuests = Math.max(
                minGuests,
                detail.getGuaranteedGuests()
        );

        int maxBilledGuests = Math.max(
                maxGuests,
                detail.getGuaranteedGuests()
        );

        BigDecimal minPrice = calculatePrice(
                detail.getRentalMin(),
                detail.getMealMin(),
                detail.getDecorationMin(),
                detail.getDecorationInRental(),
                detail.getProductionFee(),
                detail.getOtherRequiredFee(),
                minBilledGuests
        );

        BigDecimal maxPrice = calculatePrice(
                detail.getRentalMax(),
                detail.getMealMax(),
                detail.getDecorationMax(),
                detail.getDecorationInRental(),
                detail.getProductionFee(),
                detail.getOtherRequiredFee(),
                maxBilledGuests
        );

        BigDecimal vatMultiplier = switch (record.getVatStatus()) {
            case "INCLUDED" -> BigDecimal.ONE;
            case "EXCLUDED" -> new BigDecimal("1.10");
            default -> throw new IllegalArgumentException(
                    "Unsupported VAT status"
            );
        };

        minPrice = minPrice.multiply(vatMultiplier)
                .setScale(0, RoundingMode.HALF_UP);

        maxPrice = maxPrice.multiply(vatMultiplier)
                .setScale(0, RoundingMode.HALF_UP);

        return new HallPriceRange(
                minPrice,
                maxPrice,
                minBilledGuests,
                maxBilledGuests
        );
    }

    private BigDecimal calculatePrice(
            BigDecimal rental,
            BigDecimal meal,
            BigDecimal decoration,
            Boolean decorationInRental,
            BigDecimal productionFee,
            BigDecimal otherRequiredFee,
            int billedGuests
    ) {
        if (rental == null || meal == null) {
            throw new IllegalArgumentException(
                    "Rental and meal prices are required"
            );
        }

        BigDecimal mealTotal = meal.multiply(
                BigDecimal.valueOf(billedGuests)
        );

        BigDecimal decorationTotal =
                Boolean.TRUE.equals(decorationInRental)
                        ? BigDecimal.ZERO
                        : zeroIfNull(decoration);

        return rental
                .add(mealTotal)
                .add(decorationTotal)
                .add(zeroIfNull(productionFee))
                .add(zeroIfNull(otherRequiredFee));
    }

    private BigDecimal zeroIfNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private List<HallPriceDetail> selectFallbackPrices(
            List<HallPriceDetail> details,
            LocalDate weddingDate
    ) {
        LocalDate referenceDate = weddingDate != null
                ? weddingDate
                : LocalDate.now().plusMonths(12)
                .with(java.time.temporal.TemporalAdjusters.nextOrSame(
                        java.time.DayOfWeek.SATURDAY
                ));

        return details.stream()
                // 공개일이 없거나 예식일보다 늦게 공개된 가격 제외
                .filter(detail -> {
                    PriceRecord record = priceRecordRepository
                            .findById(detail.getPriceRecordId())
                            .orElseThrow();

                    return record.getPublishedAt() != null
                            && !record.getPublishedAt().isAfter(referenceDate);
                })
                // 토요일 낮 예식에 사용할 수 있는 조건만 허용
                .filter(detail -> {
                    String weekday = detail.getWeekdayStandard();

                    return "전체".equals(weekday)
                            || ("토요일".equals(weekday)
                            && referenceDate.getDayOfWeek() == java.time.DayOfWeek.SATURDAY)
                            || ("일요일".equals(weekday)
                            && referenceDate.getDayOfWeek() == java.time.DayOfWeek.SUNDAY)
                            || ("주말".equals(weekday)
                            && (referenceDate.getDayOfWeek() == java.time.DayOfWeek.SATURDAY
                            || referenceDate.getDayOfWeek() == java.time.DayOfWeek.SUNDAY));
                })
                .filter(detail -> {
                    String time = detail.getTimeStandard();
                    return "전체".equals(time)
                            || "점심".equals(time);
                })
                // 시즌 및 적용 월 확인
                .filter(detail -> {
                    String season = detail.getSeasonStandard();

                    if ("전체".equals(season)) {
                        return true;
                    }

                    if (!"기간".equals(season)) {
                        return false;
                    }

                    Short startMonth = detail.getStartMonth();
                    Short endMonth = detail.getEndMonth();

                    if (startMonth == null || endMonth == null) {
                        return false;
                    }

                    int month = referenceDate.getMonthValue();

                    if (startMonth <= endMonth) {
                        return month >= startMonth && month <= endMonth;
                    }

                    // 11월~2월처럼 연도를 넘어가는 기간
                    return month >= startMonth || month <= endMonth;
                })
                .max(Comparator.comparing(detail ->
                        priceRecordRepository
                                .findById(detail.getPriceRecordId())
                                .orElseThrow()
                                .getPublishedAt()
                ))
                .map(List::of)
                .orElseGet(List::of);
    }
    public record RankedHall(
            HallPriceResult hall,
            double totalScore
    ) {}

    public List<RankedHall> rankTopFive(
            List<RankedHall> candidates
    ) {
        if (candidates == null) {
            throw new IllegalArgumentException(
                    "Candidates must not be null"
            );
        }

        return candidates.stream()
                .sorted(
                        Comparator.comparingDouble(
                                        RankedHall::totalScore
                                ).reversed()
                                .thenComparing(
                                        ranked -> ranked.hall().hallId()
                                )
                )
                .limit(5)
                .toList();
    }
    public List<RankedHall> recommendTopFive(
            String regionCode,
            int minGuests,
            int maxGuests,
            LocalDate weddingDate,
            Long userBudget,
            Set<String> preferredMoods
    ) {
        // 1. 실제 DB에서 예식장 후보 및 예상 비용 조회
        List<HallPriceResult> candidates = findHallPrices(
                regionCode,
                minGuests,
                maxGuests,
                weddingDate
        );

        if (candidates.isEmpty()) {
            return List.of();
        }

        // 2. 예식장별 추천 점수 계산
        List<RankedHall> ranked = candidates.stream()
                .map(hall -> {
                    // 현재 DB에 홀별 분위기 태그가 없으므로 정보 없음 처리
                    Set<String> hallMoods = Set.of();

                    double totalScore = calculateHallTotalScore(
                            hall,
                            candidates,
                            userBudget,
                            preferredMoods,
                            hallMoods,
                            minGuests,
                            maxGuests
                    );

                    return new RankedHall(hall, totalScore);
                })
                .toList();

        // 3. 점수순 정렬 후 상위 5개 반환
        return rankTopFive(ranked);
    }
}
