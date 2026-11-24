
package com.wedid.backend.global.exception;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 공통 오류 응답.
 *
 * @param code    프론트엔드가 분기할 수 있는 오류 코드. 분기가 필요한 오류에만 있고, 없으면 응답에서 빠진다
 * @param message 사용자에게 보여줄 문구
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(String code, String message) {

    public ErrorResponse(String message) {
        this(null, message);
    }
}
