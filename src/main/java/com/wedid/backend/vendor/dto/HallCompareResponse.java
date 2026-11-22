
package com.wedid.backend.vendor.dto;

import java.util.List;

public record HallCompareResponse(
        int selectedCount,
        int maxSelectable,
        List<HallDetailResponse> halls
) {}
