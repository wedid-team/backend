
package com.wedid.backend.global.exception;

import com.wedid.backend.plan.service.PlanAlreadyExistsException;
import com.wedid.backend.plan.service.PlanNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.jspecify.annotations.Nullable;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(ex.getMessage()));
    }

    @ExceptionHandler(PlanNotFoundException.class)
    public ResponseEntity<ErrorResponse> handlePlanNotFound(PlanNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse(ex.getMessage()));
    }

    @ExceptionHandler(PlanAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handlePlanAlreadyExists(PlanAlreadyExistsException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse(ex.getMessage()));
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request
    ) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(FieldError::getDefaultMessage)
                .orElse("요청 값이 올바르지 않습니다.");

        return ResponseEntity.status(status).headers(headers).body(new ErrorResponse(message));
    }

    // MethodArgumentNotValidException, HttpRequestMethodNotSupportedException(405),
    // HttpMessageNotReadableException(400) 등 ResponseEntityExceptionHandler가 처리하는
    // 프레임워크 예외는 여기로 모여 원래 상태 코드를 유지한 채 공통 응답 형태로만 변환된다.
    // 그 외 처리되지 않은 예외는 상위 클래스가 500으로 넘겨준다.
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex,
            @Nullable Object body,
            HttpHeaders headers,
            HttpStatusCode statusCode,
            WebRequest request
    ) {
        if (statusCode.is5xxServerError()) {
            log.error("예상하지 못한 오류가 발생했습니다.", ex);
            return ResponseEntity.status(statusCode).headers(headers)
                    .body(new ErrorResponse("서버 내부 오류가 발생했습니다."));
        }

        String message = (body instanceof ProblemDetail problemDetail && problemDetail.getDetail() != null)
                ? problemDetail.getDetail()
                : "요청을 처리할 수 없습니다.";

        return ResponseEntity.status(statusCode).headers(headers).body(new ErrorResponse(message));
    }
}
