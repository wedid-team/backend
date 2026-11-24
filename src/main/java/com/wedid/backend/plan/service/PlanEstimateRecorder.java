package com.wedid.backend.plan.service;

import com.wedid.backend.estimate.dto.EstimateRequest;
import com.wedid.backend.estimate.dto.HallCostEstimate;
import com.wedid.backend.estimate.entity.CalculationPolicy;
import com.wedid.backend.estimate.entity.EstimateRun;
import com.wedid.backend.estimate.entity.EstimateRun.CalculationStatus;
import com.wedid.backend.estimate.entity.EstimateRun.Range;
import com.wedid.backend.estimate.entity.SdmBudgetBand;
import com.wedid.backend.estimate.service.EstimateService;
import com.wedid.backend.plan.dto.PlanRequest;
import com.wedid.backend.plan.entity.PlanConditions;
import com.wedid.backend.plan.entity.PlanSchedule;
import com.wedid.backend.sdm.service.SdmEstimateCalculator;
import com.wedid.backend.sdm.service.SdmEstimateCalculator.SdmEstimate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 계획 조건으로 예상 비용을 계산해 고정 기록(estimate_runs)을 만든다.
 *
 * <p>스드메 예상 비용은 고른 예산대 안 조합 합계의 하위 25% ~ 75% 지점이다(SdmEstimateCalculator).
 * 스드메 가격 데이터가 없으면 예산대 경계값으로 대신하고(sourceType으로 구분), 경계가 미정이면 null이다.
 * 스드메가 미정이면 예식장을 계산했어도 PARTIAL, 예식장도 계산할 수 없으면 UNAVAILABLE이다.
 * 합계는 어느 한쪽이라도 없으면 정상 총액으로 보이지 않도록 null로 둔다(db.md 7.2).
 */
@Component
public class PlanEstimateRecorder {

    private final EstimateService estimateService;
    private final SdmEstimateCalculator sdmEstimateCalculator;
    private final JsonMapper jsonMapper;

    public PlanEstimateRecorder(
            EstimateService estimateService,
            SdmEstimateCalculator sdmEstimateCalculator,
            JsonMapper jsonMapper
    ) {
        this.estimateService = estimateService;
        this.sdmEstimateCalculator = sdmEstimateCalculator;
        this.jsonMapper = jsonMapper;
    }

    public EstimateRun record(
            UUID planId,
            UUID userId,
            PlanRequest request,
            PlanConditions conditions,
            CalculationPolicy policy,
            SdmBudgetBand band,
            OffsetDateTime now
    ) {
        PlanSchedule schedule = conditions.schedule();

        // 미정 날짜는 계획에 저장한 기준일로 계산해야 하므로, 계산 날짜를 직접 정해 넘긴다.
        EstimateRequest hallRequest = new EstimateRequest(
                request.regionCode(),
                request.guestRangeCode(),
                EstimateRequest.WeddingDateType.EXACT,
                schedule.calculationDate(),
                null,
                null,
                request.budget(),
                conditions.preferredMoods()
        );
        Optional<HallCostEstimate> hall = estimateService.calculateHallCostEstimate(hallRequest);

        Range venue = hall.map(h -> new Range(h.minPrice(), h.maxPrice())).orElse(Range.UNKNOWN);
        SdmEstimate sdmEstimate = sdmEstimateCalculator.estimate(policy, band);
        Range sdm = sdmEstimate.isKnown() ? new Range(sdmEstimate.min(), sdmEstimate.max()) : Range.UNKNOWN;
        Range total = venue.isKnown() && sdm.isKnown()
                ? new Range(venue.min().add(sdm.min()), venue.max().add(sdm.max()))
                : Range.UNKNOWN;

        CalculationStatus status = !venue.isKnown() ? CalculationStatus.UNAVAILABLE
                : !sdm.isKnown() ? CalculationStatus.PARTIAL
                : CalculationStatus.COMPLETE;

        return new EstimateRun(
                planId,
                userId,
                policy.getId(),
                band.getId(),
                conditions.regionId(),
                toJson(inputSnapshot(request, conditions, band)),
                toJson(resultSnapshot(hall, sdmEstimate, total, band)),
                toJson(sourceSnapshot(hall, policy)),
                venue,
                sdm,
                total,
                status,
                now
        );
    }

    private Map<String, Object> inputSnapshot(PlanRequest request, PlanConditions conditions, SdmBudgetBand band) {
        PlanSchedule schedule = conditions.schedule();
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("regionCode", request.regionCode());
        input.put("guestRangeCode", request.guestRangeCode());
        input.put("guestChoice", conditions.guestChoice().name());
        input.put("datePrecision", schedule.datePrecision().name());
        input.put("weddingDate", text(schedule.weddingDate()));
        input.put("weddingMonth", text(schedule.weddingMonth()));
        input.put("assumptionDate", text(schedule.assumptionDate()));
        input.put("effectiveWeddingDate", text(schedule.effectiveWeddingDate()));
        input.put("calculationDate", text(schedule.calculationDate()));
        input.put("estimatedDate", schedule.isEstimatedDate());
        input.put("dayTimeBasis", "SATURDAY_DAYTIME");
        input.put("budget", conditions.budgetAmount());
        input.put("moods", conditions.preferredMoods());
        input.put("sdmBandCode", band.getCode());
        return input;
    }

    private Map<String, Object> resultSnapshot(
            Optional<HallCostEstimate> hall, SdmEstimate sdm, Range total, SdmBudgetBand band
    ) {
        Map<String, Object> venue = new LinkedHashMap<>();
        hall.ifPresentOrElse(h -> {
            venue.put("status", "CALCULATED");
            venue.put("min", h.minPrice());
            venue.put("max", h.maxPrice());
            venue.put("sourceType", h.priceSource());
            venue.put("referenceMonth", text(h.referenceMonth()));
            venue.put("sourceRegion", h.regionName());
            venue.put("sourceUrl", h.sourceUrl());
            venue.put("sampleCount", h.sampleCount());
        }, () -> venue.put("status", "UNAVAILABLE"));

        Map<String, Object> sdmResult = new LinkedHashMap<>();
        sdmResult.put("status", sdm.isKnown() ? "CALCULATED" : "UNDECIDED");
        sdmResult.put("bandCode", band.getCode());
        sdmResult.put("bandLabel", band.getLabel());
        sdmResult.put("min", sdm.isKnown() ? sdm.min() : null);
        sdmResult.put("max", sdm.isKnown() ? sdm.max() : null);
        sdmResult.put("sourceType", sdm.sourceType() == null ? null : sdm.sourceType().name());
        sdmResult.put("pendingReason", sdm.pendingReason() == null ? null : sdm.pendingReason().name());
        sdmResult.put("combinationCountInTier", sdm.combinationCountInTier());

        Map<String, Object> totalResult = new LinkedHashMap<>();
        totalResult.put("min", total.min());
        totalResult.put("max", total.max());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("venue", venue);
        result.put("sdm", sdmResult);
        result.put("total", totalResult);
        return result;
    }

    private Map<String, Object> sourceSnapshot(Optional<HallCostEstimate> hall, CalculationPolicy policy) {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("policyVersion", policy.getVersion());
        source.put("policyStatus", policy.getStatus());
        source.put("venueSourceType", hall.map(HallCostEstimate::priceSource).orElse(null));
        source.put("venueSourceUrl", hall.map(HallCostEstimate::sourceUrl).orElse(null));
        return source;
    }

    private String toJson(Map<String, Object> value) {
        return jsonMapper.writeValueAsString(value);
    }

    private static String text(Object value) {
        return value == null ? null : value.toString();
    }
}
