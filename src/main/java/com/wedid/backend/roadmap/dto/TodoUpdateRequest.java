package com.wedid.backend.roadmap.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Pattern;

/**
 * 할 일 체크 · 담당 변경. 보낸 항목만 바꾼다.
 *
 * @param completed 완료 여부. 생략하면 그대로 둔다
 * @param assignee  담당(P-07), 요청한 사람 기준: ME(나) / PARTNER(짝꿍) / BOTH(함께) / UNASSIGNED(담당 없음).
 *                  생략하면 그대로 둔다
 */
public record TodoUpdateRequest(
        Boolean completed,

        @Pattern(regexp = "ME|PARTNER|BOTH|UNASSIGNED", message = "올바른 담당을 선택해주세요.")
        String assignee
) {

    @AssertTrue(message = "바꿀 항목을 보내주세요.")
    public boolean isAnyFieldPresent() {
        return completed != null || assignee != null;
    }
}
