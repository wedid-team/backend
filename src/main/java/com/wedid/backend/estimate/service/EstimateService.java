
package com.wedid.backend.estimate.service;

import com.wedid.backend.vendor.entity.Hall;
import com.wedid.backend.vendor.entity.HallPriceDetail;
import com.wedid.backend.vendor.entity.PriceRecord;
import com.wedid.backend.vendor.entity.Vendor;
import com.wedid.backend.vendor.repository.HallPriceDetailRepository;
import com.wedid.backend.vendor.repository.HallRepository;
import com.wedid.backend.vendor.repository.PriceRecordRepository;
import com.wedid.backend.vendor.repository.VendorRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import com.wedid.backend.estimate.region.Region;
import com.wedid.backend.estimate.region.RegionRepository;

import com.wedid.backend.estimate.dto.EstimateRequest;
import com.wedid.backend.estimate.dto.GuestRange;

import com.wedid.backend.estimate.entity.MarketStatistic;
import com.wedid.backend.estimate.repository.MarketStatisticsRepository;

import com.wedid.backend.estimate.dto.HallCostEstimate;

@Service
public class EstimateService {

    private final VendorRepository vendorRepository;
    private final HallRepository hallRepository;
    private final PriceRecordRepository priceRecordRepository;
    private final HallPriceDetailRepository hallPriceDetailRepository;
    private final RegionRepository regionRepository;
    private final WeddingDateResolver weddingDateResolver;
    private final MarketStatisticsRepository marketStatisticsRepository;


    public EstimateService(
            VendorRepository vendorRepository,
            HallRepository hallRepository,
            PriceRecordRepository priceRecordRepository,
            HallPriceDetailRepository hallPriceDetailRepository,
            RegionRepository regionRepository,
            WeddingDateResolver weddingDateResolver,
            MarketStatisticsRepository marketStatisticsRepository
    ) {
        this.vendorRepository = vendorRepository;
        this.hallRepository = hallRepository;
        this.priceRecordRepository = priceRecordRepository;
        this.hallPriceDetailRepository = hallPriceDetailRepository;
        this.regionRepository = regionRepository;
        this.weddingDateResolver = weddingDateResolver;
        this.marketStatisticsRepository = marketStatisticsRepository;
    }


    public Short resolveRegionId(String regionCode) {
        if (regionCode == null || regionCode.isBlank()) {
            throw new IllegalArgumentException(
                    "지역 코드는 필수입니다."
            );
        }

        return regionRepository.findByCode(regionCode)
                .map(Region::getId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "지역 정보를 찾을 수 없습니다: " + regionCode
                        )
                );
    }

    // 1. 웨딩홀 비용 계산
    public BigDecimal calculateHallCost(
            BigDecimal rentalFee,
            BigDecimal decorationFee,
            boolean decorationIncluded,
            BigDecimal mealPrice,
            int guestCount,
            int guaranteedGuests,
            BigDecimal otherRequiredFee
    ) {
        int chargedGuests = Math.max(
                guestCount,
                guaranteedGuests
        );

        BigDecimal decorationCost = decorationIncluded
                ? BigDecimal.ZERO
                : decorationFee;

        return rentalFee
                .add(decorationCost)
                .add(mealPrice.multiply(
                        BigDecimal.valueOf(chargedGuests)
                ))
                .add(otherRequiredFee);
    }

    // 2. VAT 적용
    public BigDecimal applyVat(
            BigDecimal amount,
            String vatStatus
    ) {
        if (vatStatus == null) {
            throw new IllegalArgumentException(
                    "VAT 상태가 없습니다."
            );
        }

        return switch (vatStatus) {
            case "INCLUDED" -> amount;
            case "EXCLUDED" -> amount.multiply(
                    new BigDecimal("1.10")
            );
            case "UNKNOWN" -> amount;
            default -> throw new IllegalArgumentException(
                    "지원하지 않는 VAT 상태: " + vatStatus
            );
        };
    }

    // 3. 지역별 운영 중인 웨딩홀 업체 조회
    public List<Vendor> findOperatingWeddingHalls(
            Short regionId
    ) {
        return vendorRepository
                .findByCategoryAndOperatingStatusRawAndRegionId(
                        "WEDDING_HALL",
                        "운영",
                        regionId
                );
    }

    // 4. 업체별 홀 조회
    public List<Hall> findHallsByVendor(Long vendorId) {
        return hallRepository.findByVendorId(vendorId);
    }

    // 5. 홀별 가격 기록 조회
    public List<PriceRecord> findPriceRecordsByHall(
            Long hallId
    ) {
        return priceRecordRepository.findByHallId(hallId);
    }

    // 6. 가격 상세 정보 조회
    public Optional<HallPriceDetail> findPriceDetail(
            Long priceRecordId
    ) {
        return hallPriceDetailRepository
                .findByPriceRecordId(priceRecordId);
    }

    // 7. 가격 기록 1건의 예상 비용 계산
    public BigDecimal calculatePriceRecordCost(
            Long priceRecordId,
            int guestCount
    ) {
        if (guestCount <= 0) {
            throw new IllegalArgumentException(
                    "하객 수는 1명 이상이어야 합니다."
            );
        }

        PriceRecord priceRecord = priceRecordRepository
                .findById(priceRecordId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "가격 기록을 찾을 수 없습니다."
                        )
                );

        HallPriceDetail detail = findPriceDetail(priceRecordId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "가격 상세 정보를 찾을 수 없습니다."
                        )
                );


        if (detail.getCapacityMin() == null
                || detail.getCapacityMax() == null
                || guestCount < detail.getCapacityMin()
                || guestCount > detail.getCapacityMax()) {
            throw new IllegalArgumentException(
                    "하객 수가 웨딩홀 수용 가능 범위를 벗어났습니다."
            );
        }


        BigDecimal otherFee = detail.getOtherRequiredFee() == null
                ? BigDecimal.ZERO
                : detail.getOtherRequiredFee();

        BigDecimal hallCost = calculateHallCost(
                detail.getRentalMin(),
                detail.getDecorationMin(),
                Boolean.TRUE.equals(
                        detail.getDecorationInRental()
                ),
                detail.getMealMin(),
                guestCount,
                detail.getGuaranteedGuests(),
                otherFee
        );

        return applyVat(
                hallCost,
                priceRecord.getVatStatus()
        );
    }

    // 8. 지역별 전체 가격 기록 조회
    public List<PriceRecord> findPriceRecordsByRegion(
            Short regionId
    ) {
        List<Vendor> vendors =
                findOperatingWeddingHalls(regionId);

        List<PriceRecord> result = new ArrayList<>();

        for (Vendor vendor : vendors) {

            List<Hall> halls =
                    findHallsByVendor(vendor.getId());

            for (Hall hall : halls) {

                List<PriceRecord> records =
                        findPriceRecordsByHall(hall.getId());

                result.addAll(records);
            }
        }

        return result;
    }

    // 9. 최대 하객 수를 수용할 수 있는 가격 기록 조회
    public List<PriceRecord> findSuitablePriceRecords(
            Short regionId,
            int maxGuests
    ) {
        if (maxGuests <= 0) {
            throw new IllegalArgumentException(
                    "하객 수는 1명 이상이어야 합니다."
            );
        }

        List<PriceRecord> records =
                findPriceRecordsByRegion(regionId);

        List<PriceRecord> suitableRecords =
                new ArrayList<>();

        for (PriceRecord record : records) {

            Optional<HallPriceDetail> detailOptional =
                    findPriceDetail(record.getId());

            if (detailOptional.isEmpty()) {
                continue;
            }

            HallPriceDetail detail = detailOptional.get();


            if (detail.getCapacityMin() == null
                    || detail.getCapacityMax() == null) {
                continue;
            }

            if (detail.getCapacityMin() <= maxGuests
                    && detail.getCapacityMax() >= maxGuests) {
                suitableRecords.add(record);
            }

        }

        return suitableRecords;
    }

    // 10. 웨딩홀 최소~최대 예상 비용 계산
    public BigDecimal[] calculateHallCostRange(
            Long priceRecordId,
            int minGuests,
            int maxGuests
    ) {
        if (minGuests <= 0 || maxGuests < minGuests) {
            throw new IllegalArgumentException(
                    "올바르지 않은 하객 수 구간입니다."
            );
        }

        PriceRecord record = priceRecordRepository
                .findById(priceRecordId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "가격 기록을 찾을 수 없습니다."
                        )
                );

        HallPriceDetail detail = findPriceDetail(priceRecordId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "가격 상세 정보가 없습니다."
                        )
                );


        if (detail.getCapacityMin() == null
                || detail.getCapacityMax() == null
                || detail.getCapacityMin() > maxGuests
                || detail.getCapacityMax() < maxGuests) {
            throw new IllegalArgumentException(
                    "선택한 하객 수 구간에 적합하지 않은 웨딩홀입니다."
            );
        }


        int guaranteed = detail.getGuaranteedGuests();

        int effectiveMinGuests = Math.max(
                minGuests,
                detail.getCapacityMin()
        );


        BigDecimal rentalMin = detail.getRentalMin();

        BigDecimal rentalMax = detail.getRentalMax() != null
                ? detail.getRentalMax()
                : rentalMin;

        BigDecimal mealMin = detail.getMealMin();

        BigDecimal mealMax = detail.getMealMax() != null
                ? detail.getMealMax()
                : mealMin;

        boolean included = Boolean.TRUE.equals(
                detail.getDecorationInRental()
        );

        BigDecimal decorationMin = included
                ? BigDecimal.ZERO
                : detail.getDecorationMin();

        BigDecimal decorationMax = included
                ? BigDecimal.ZERO
                : (detail.getDecorationMax() != null
                ? detail.getDecorationMax()
                : detail.getDecorationMin());

        BigDecimal otherFee = detail.getOtherRequiredFee() != null
                ? detail.getOtherRequiredFee()
                : BigDecimal.ZERO;

        BigDecimal minCost = rentalMin
                .add(decorationMin)
                .add(mealMin.multiply(
                        BigDecimal.valueOf(
                                Math.max(effectiveMinGuests, guaranteed)
                        )
                ))
                .add(otherFee);

        BigDecimal maxCost = rentalMax
                .add(decorationMax)
                .add(mealMax.multiply(
                        BigDecimal.valueOf(
                                Math.max(maxGuests, guaranteed)
                        )
                ))
                .add(otherFee);

        return new BigDecimal[]{
                applyVat(minCost, record.getVatStatus()),
                applyVat(maxCost, record.getVatStatus())
        };
    }

    // 11. Q25, Q75 백분위수 계산
    public BigDecimal calculatePercentile(
            List<BigDecimal> values,
            double percentile
    ) {
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException(
                    "계산할 가격 데이터가 없습니다."
            );
        }

        if (percentile < 0 || percentile > 1) {
            throw new IllegalArgumentException(
                    "백분위수는 0~1 사이여야 합니다."
            );
        }

        List<BigDecimal> sorted =
                new ArrayList<>(values);

        Collections.sort(sorted);

        double position = (sorted.size() - 1) * percentile;

        int lowerIndex = (int) Math.floor(position);
        int upperIndex = (int) Math.ceil(position);

        BigDecimal lower = sorted.get(lowerIndex);
        BigDecimal upper = sorted.get(upperIndex);

        if (lowerIndex == upperIndex) {
            return lower;
        }

        BigDecimal weight = BigDecimal.valueOf(
                position - lowerIndex
        );

        return lower.add(
                upper.subtract(lower).multiply(weight)
        ).setScale(2, RoundingMode.HALF_UP);
    }

    // 12. 적용 연도와 월 조건 검사

    public boolean matchesDateCondition(
            Integer applicableYear,
            Integer startMonth,
            Integer endMonth,
            LocalDate weddingDate
    ) {
        if (weddingDate == null) {
            throw new IllegalArgumentException(
                    "예식 날짜가 필요합니다."
            );
        }

        if (applicableYear != null
                && !applicableYear.equals(weddingDate.getYear())) {
            return false;
        }

        int month = weddingDate.getMonthValue();

        if (startMonth != null && endMonth != null) {
            if (startMonth <= endMonth) {
                return month >= startMonth
                        && month <= endMonth;
            }

            return month >= startMonth
                    || month <= endMonth;
        }

        if (startMonth != null) {
            return month >= startMonth;
        }

        if (endMonth != null) {
            return month <= endMonth;
        }

        return true;
    }

    // 13. 요일과 시간 조건 검사
    public boolean matchesDayAndTime(
            String weekday,
            String time,
            LocalDate weddingDate
    ) {
        if (weddingDate == null) {
            throw new IllegalArgumentException(
                    "예식 날짜가 필요합니다."
            );
        }

        DayOfWeek day = weddingDate.getDayOfWeek();

        boolean dayMatches;

        if (weekday == null || weekday.equals("전체")) {
            dayMatches = true;
        } else {
            dayMatches = switch (weekday) {
                case "토요일" ->
                        day == DayOfWeek.SATURDAY;
                case "일요일" ->
                        day == DayOfWeek.SUNDAY;
                case "주말" ->
                        day == DayOfWeek.SATURDAY
                                || day == DayOfWeek.SUNDAY;
                case "평일" ->
                        day != DayOfWeek.SATURDAY
                                && day != DayOfWeek.SUNDAY;
                default -> false;
            };
        }

        boolean timeMatches = time == null
                || time.equals("전체")
                || time.equals("점심");

        return dayMatches && timeMatches;
    }

    // 14. 예식 날짜에 적합한 가격 기록 선택
    public Optional<PriceRecord> selectBestPriceRecord(
            List<PriceRecord> records,
            LocalDate weddingDate
    ) {
        if (records == null || records.isEmpty()) {
            return Optional.empty();
        }

        if (weddingDate == null) {
            throw new IllegalArgumentException(
                    "예식 날짜가 필요합니다."
            );
        }

        List<PriceRecord> matchedRecords = new ArrayList<>();

        for (PriceRecord record : records) {

            Optional<HallPriceDetail> detailOptional =
                    findPriceDetail(record.getId());

            if (detailOptional.isEmpty()) {
                continue;
            }

            HallPriceDetail detail = detailOptional.get();

            // 연도 및 월 조건 검사
            if (!matchesDateCondition(
                    detail.getApplicableYear(),
                    detail.getStartMonth() == null
                            ? null : detail.getStartMonth().intValue(),
                    detail.getEndMonth() == null
                            ? null : detail.getEndMonth().intValue(),
                    weddingDate
            )) {
                continue;
            }

            // 요일 및 시간 조건 검사
            if (!matchesDayAndTime(
                    detail.getWeekdayStandard(),
                    detail.getTimeStandard(),
                    weddingDate
            )) {
                continue;
            }

            // 시즌 적용 월이 불명확하면 자동 선택 제외
            String season = detail.getSeasonStandard();

            boolean hasMonthRange =
                    detail.getStartMonth() != null
                            && detail.getEndMonth() != null;

            if (season != null
                    && !season.equals("전체")
                    && !hasMonthRange) {
                continue;
            }

            matchedRecords.add(record);
        }

        // 조건이 일치하는 기록이 없다면 선택하지 않음
        if (matchedRecords.isEmpty()) {
            return Optional.empty();
        }

        // 가장 구체적인 조건을 가진 기록 선택

        return matchedRecords.stream()
                .max(
                        Comparator.comparingInt(
                                (PriceRecord record) ->
                                        calculateConditionScore(record.getId())
                        ).thenComparing(
                                PriceRecord::getPublishedAt,
                                Comparator.nullsFirst(
                                        Comparator.naturalOrder()
                                )
                        ).thenComparing(
                                PriceRecord::getId
                        )
                );

    }

    // 15. 가격 기록 조건의 구체성 점수

    private int calculateConditionScore(Long priceRecordId) {

        HallPriceDetail detail = findPriceDetail(priceRecordId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "가격 상세 정보가 없습니다."
                        )
                );

        int score = 0;

        if (detail.getApplicableYear() != null) {
            score += 1;
        }

        if (detail.getStartMonth() != null
                && detail.getEndMonth() != null) {
            score += 2;
        }

        String weekday = detail.getWeekdayStandard();

        if ("토요일".equals(weekday)
                || "일요일".equals(weekday)
                || "평일".equals(weekday)) {
            score += 2;
        } else if ("주말".equals(weekday)) {
            score += 1;
        }

        if ("점심".equals(detail.getTimeStandard())) {
            score += 2;
        }

        return score;
    }


    public List<PriceRecord> findSelectedPriceRecords(
            Short regionId,
            int maxGuests,
            LocalDate weddingDate
    ) {
        if (weddingDate == null) {
            throw new IllegalArgumentException(
                    "예식 날짜가 필요합니다."
            );
        }

        // 1. 지역과 수용 인원에 맞는 가격 기록 조회
        List<PriceRecord> suitableRecords =
                findSuitablePriceRecords(regionId, maxGuests);

        // 2. 동일한 웨딩홀의 가격 기록끼리 묶기
        java.util.Map<Long, List<PriceRecord>> recordsByHall =
                new java.util.HashMap<>();

        for (PriceRecord record : suitableRecords) {
            if (record.getHallId() == null) {
                continue;
            }

            recordsByHall
                    .computeIfAbsent(
                            record.getHallId(),
                            id -> new java.util.ArrayList<>()
                    )
                    .add(record);
        }

        // 3. 웨딩홀별 적합한 가격 기록 하나 선택
        List<PriceRecord> selectedRecords =
                new java.util.ArrayList<>();

        for (List<PriceRecord> hallRecords : recordsByHall.values()) {

            Optional<PriceRecord> selected =
                    selectBestPriceRecord(
                            hallRecords,
                            weddingDate
                    );

            selected.ifPresent(selectedRecords::add);
        }

        // 4. 결과를 홀 ID 순서로 정렬
        selectedRecords.sort(
                java.util.Comparator.comparing(
                        PriceRecord::getHallId
                )
        );

        return selectedRecords;
    }

    public Optional<BigDecimal[]> calculateRegionalHallCostRange(
            Short regionId,
            int minGuests,
            int maxGuests,
            LocalDate weddingDate
    ) {
        if (regionId == null) {
            throw new IllegalArgumentException(
                    "지역은 필수입니다."
            );
        }

        if (minGuests <= 0 || maxGuests < minGuests) {
            throw new IllegalArgumentException(
                    "올바르지 않은 하객 수 구간입니다."
            );
        }

        if (weddingDate == null) {
            throw new IllegalArgumentException(
                    "예식 날짜가 필요합니다."
            );
        }

        // 지역, 수용 인원, 날짜에 맞는 홀별 가격 기록
        List<PriceRecord> selectedRecords =
                findSelectedPriceRecords(
                        regionId,
                        maxGuests,
                        weddingDate
                );

        List<BigDecimal> hallMidpoints = new ArrayList<>();

        for (PriceRecord record : selectedRecords) {

            Optional<HallPriceDetail> detailOptional =
                    findPriceDetail(record.getId());

            if (detailOptional.isEmpty()) {
                continue;
            }

            HallPriceDetail detail = detailOptional.get();

            // 필수 가격 정보가 없으면 계산 대상에서 제외
            if (detail.getRentalMin() == null
                    || detail.getMealMin() == null
                    || detail.getGuaranteedGuests() == null
                    || (Boolean.FALSE.equals(
                    detail.getDecorationInRental()
            ) && detail.getDecorationMin() == null)
                    || detail.getDecorationInRental() == null) {
                continue;
            }

            // 0원 대관료가 미공개 가격인지 확인 필요
            // 현재는 0원 자체를 임의로 누락 처리하지 않음

            BigDecimal[] costRange =
                    calculateHallCostRange(
                            record.getId(),
                            minGuests,
                            maxGuests
                    );

            BigDecimal midpoint = costRange[0]
                    .add(costRange[1])
                    .divide(
                            BigDecimal.valueOf(2),
                            2,
                            RoundingMode.HALF_UP
                    );

            hallMidpoints.add(midpoint);
        }

        // 유효한 웨딩홀이 10곳 미만이면 참가격 통계 사용
        if (hallMidpoints.size() < 10) {
            return calculateMarketHallCostRange(regionId);
        }

        BigDecimal q25 =
                calculatePercentile(hallMidpoints, 0.25);

        BigDecimal q75 =
                calculatePercentile(hallMidpoints, 0.75);

        return Optional.of(
                new BigDecimal[]{q25, q75}
        );
    }

    public Optional<BigDecimal[]> calculateEstimateHallRange(
            EstimateRequest request
    ) {
        // 1. 사용자 지역 코드를 DB 지역 ID로 변환
        Short regionId = resolveRegionId(request.regionCode());

        // 2. 사용자 하객 수 선택을 계산용 범위로 변환
        GuestRange guestRange =
                GuestRange.fromCode(request.guestRangeCode());

        // 3. 예식 날짜를 계산 기준 날짜로 변환
        WeddingDateResolver.ResolvedWeddingDate resolvedDate =
                weddingDateResolver.resolve(request);

        // 4. 실제 DB 데이터를 이용해 웨딩홀 비용 범위 계산
        return calculateRegionalHallCostRange(
                regionId,
                guestRange.minGuests(),
                guestRange.maxGuests(),
                resolvedDate.calculationDate()
        );
    }
    // 16. 참가격 통계 기반 웨딩홀 예상 비용 조회
    public Optional<BigDecimal[]> calculateMarketHallCostRange(
            Short regionId
    ) {
        Optional<MarketStatistic> statistic =
                marketStatisticsRepository
                        .findFirstByScopeTypeAndRegionIdAndCategoryRawAndItemRawAndUnitRawAndP25IsNotNullAndP75IsNotNullOrderByReferenceMonthDescIdDesc(
                                "REGION",
                                regionId,
                                "예식장",
                                "총 계약금액",
                                "원"
                        );

        if (statistic.isEmpty()) {
            statistic = marketStatisticsRepository
                    .findFirstByScopeTypeAndCategoryRawAndItemRawAndUnitRawAndP25IsNotNullAndP75IsNotNullOrderByReferenceMonthDescIdDesc(
                            "NATIONAL",
                            "예식장",
                            "총 계약금액",
                            "원"
                    );
        }

        return statistic.map(data -> new BigDecimal[]{
                data.getP25(),
                data.getP75()
        });
    }
    // 17. 웨딩홀 예상 비용 및 가격 출처 반환
    public Optional<HallCostEstimate> calculateHallCostEstimate(
            EstimateRequest request
    ) {
        Short regionId = resolveRegionId(request.regionCode());

        GuestRange guestRange =
                GuestRange.fromCode(request.guestRangeCode());

        WeddingDateResolver.ResolvedWeddingDate resolvedDate =
                weddingDateResolver.resolve(request);

        int validHallCount = countValidHallPrices(
                regionId,
                guestRange.minGuests(),
                guestRange.maxGuests(),
                resolvedDate.calculationDate()
        );

        Optional<BigDecimal[]> hallRange =
                calculateRegionalHallCostRange(
                        regionId,
                        guestRange.minGuests(),
                        guestRange.maxGuests(),
                        resolvedDate.calculationDate()
                );

        if (hallRange.isEmpty()) {
            return Optional.empty();
        }

        BigDecimal[] range = hallRange.get();

        // 실제 웨딩홀 데이터가 10곳 이상이면 직접 계산한 결과
        if (validHallCount >= 10) {
            return Optional.of(new HallCostEstimate(
                    range[0],
                    range[1],
                    "HALL_DATA",
                    null,
                    null,
                    null,
                    validHallCount
            ));
        }

        // 10곳 미만이면 참가격 통계에서 기준일과 출처 조회
        Optional<MarketStatistic> statistic =
                marketStatisticsRepository
                        .findFirstByScopeTypeAndRegionIdAndCategoryRawAndItemRawAndUnitRawAndP25IsNotNullAndP75IsNotNullOrderByReferenceMonthDescIdDesc(
                                "REGION",
                                regionId,
                                "예식장",
                                "총 계약금액",
                                "원"
                        );

        if (statistic.isEmpty()) {
            statistic = marketStatisticsRepository
                    .findFirstByScopeTypeAndCategoryRawAndItemRawAndUnitRawAndP25IsNotNullAndP75IsNotNullOrderByReferenceMonthDescIdDesc(
                            "NATIONAL",
                            "예식장",
                            "총 계약금액",
                            "원"
                    );
        }

        return statistic.map(data -> new HallCostEstimate(
                range[0],
                range[1],
                "MARKET_STATISTICS",
                data.getReferenceMonth(),
                data.getRegionRaw(),
                data.getSourceUrl(),
                null
        ));
    }
    // 18. 실제 웨딩홀 가격 데이터로 계산 가능한 홀 수 확인
    private int countValidHallPrices(
            Short regionId,
            int minGuests,
            int maxGuests,
            LocalDate weddingDate
    ) {
        List<PriceRecord> selectedRecords =
                findSelectedPriceRecords(regionId, maxGuests, weddingDate);

        int count = 0;

        for (PriceRecord record : selectedRecords) {
            Optional<HallPriceDetail> detailOptional =
                    findPriceDetail(record.getId());

            if (detailOptional.isEmpty()) {
                continue;
            }

            HallPriceDetail detail = detailOptional.get();

            if (detail.getRentalMin() == null
                    || detail.getMealMin() == null
                    || detail.getGuaranteedGuests() == null
                    || detail.getDecorationInRental() == null
                    || (Boolean.FALSE.equals(detail.getDecorationInRental())
                    && detail.getDecorationMin() == null)) {
                continue;
            }

            count++;
        }

        return count;
    }

}
