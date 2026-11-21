
package com.wedid.backend.vendor.dto;

import java.util.List;

public record HallListResponse(
        List<HallSummaryResponse> halls,
        List<HallSummaryResponse> insufficientHalls,
        int totalCount,
        int insufficientCount,
        int page,
        int size,
        int totalPages,
        boolean hasNext
) {
}
