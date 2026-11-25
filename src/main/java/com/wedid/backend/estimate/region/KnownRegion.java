package com.wedid.backend.estimate.region;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 지역 코드가 DB regions 테이블에 있는지 확인한다. (GET /api/v1/regions가 주는 목록과 같은 기준)
 * null·빈 값은 통과시키고 @NotBlank가 따로 막는다.
 */
@Documented
@Constraint(validatedBy = KnownRegionValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface KnownRegion {

    String message() default "올바른 지역을 선택해주세요.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
