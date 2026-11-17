
package com.wedid.backend.estimate.service;

import com.wedid.backend.estimate.dto.EstimateRequest;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;

@Component
public class WeddingDateResolver {

    private final Clock clock;

    public WeddingDateResolver() {
        this.clock = Clock.systemDefaultZone();
    }

    public ResolvedWeddingDate resolve(EstimateRequest request) {
        return switch (request.weddingDateType()) {
            case EXACT -> new ResolvedWeddingDate(
                    request.weddingDate(),
                    request.weddingDate(),
                    false
            );

            case MONTH -> {
                YearMonth month = YearMonth.of(
                        request.weddingYear(),
                        request.weddingMonth()
                );

                LocalDate referenceDate = month.atDay(1)
                        .with(TemporalAdjusters.nextOrSame(
                                DayOfWeek.SATURDAY
                        ));

                yield new ResolvedWeddingDate(
                        null,
                        referenceDate,
                        true
                );
            }

            case UNDECIDED -> {
                LocalDate referenceDate = LocalDate.now(clock)
                        .plusMonths(12)
                        .with(TemporalAdjusters.nextOrSame(
                                DayOfWeek.SATURDAY
                        ));

                yield new ResolvedWeddingDate(
                        null,
                        referenceDate,
                        true
                );
            }
        };
    }

    public record ResolvedWeddingDate(
            LocalDate actualWeddingDate,
            LocalDate calculationDate,
            boolean estimatedDate
    ) {
    }
}
