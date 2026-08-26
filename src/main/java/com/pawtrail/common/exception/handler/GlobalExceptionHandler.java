package com.pawtrail.common.exception.handler;

import com.pawtrail.common.exception.CommonErrorCode;
import com.pawtrail.common.exception.CustomException;
import com.pawtrail.common.exception.ErrorCode;
import com.pawtrail.common.response.CommonApiResponse;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // CustomException이 아닌 경우 CommonApiResponse error의 두번째 인자 T data용 record
    public record FieldErrorDetail(String field, String message) {}

    // Case 1. CustomException 예외
    @ExceptionHandler(CustomException.class)
    public ResponseEntity<CommonApiResponse<?>> handleCustomException(CustomException e) {
        log.warn("CustomException 발생: {}", e.getMessage());

        return ResponseEntity
                .status(e.getErrorCode().getHttpStatus())
                .body(CommonApiResponse.error(
                        e.getErrorCode()
                ));
    }

    // Case 2. Valid 실패 예외
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<CommonApiResponse<?>> handleValidationException(MethodArgumentNotValidException e) {
        log.warn("Validation 에러 발생: {}", e.getMessage());

        // CustomException이 아니라서 직접 CommonErrorCode에서 가져옴
        ErrorCode errorCode = CommonErrorCode.VALIDATION_FAILED;

        // T data에 들어갈 세부 응답들의 List
        List<FieldErrorDetail> errorDetails =
                // json을 DTO로 바인딩한 결과인 BindingResult를 가져와서 틀린걸 가져옴
                // 이후 틀린 필드와 기본 오류 안내 메시지를 FieldErrorDetail(=T data) 형태로 담음
                e.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldErrorDetail(error.getField(), error.getDefaultMessage())).toList();

        return ResponseEntity
                .status(errorCode.getHttpStatus())
                .body(CommonApiResponse.error(errorCode, errorDetails));
    }

    // Case 3. 경로 및 쿼리 파라미터 타입 오류
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<CommonApiResponse<?>> handleTypeMismatchException(MethodArgumentTypeMismatchException e) {
        log.warn("Type Mismatch 에러 발생: 파라미터명 '{}', 값 '{}'", e.getName(), e.getValue());

        // CustomException이 아니라서 직접 CommonErrorCode에서 가져옴
        ErrorCode errorCode = CommonErrorCode.VALIDATION_FAILED;

        // T data에 들어갈 세부 정보
        FieldErrorDetail errorDetail = new FieldErrorDetail(e.getName(), "타입이 올바르지 않습니다. (입력값: " + e.getValue() + ")");

        // T data에 들어갈 세부 정보들을 모아서 List로 생성
        List<FieldErrorDetail> errorDetails = List.of(errorDetail);

        return ResponseEntity
                .status(errorCode.getHttpStatus())
                .body(CommonApiResponse.error(errorCode, errorDetails));
    }

    // Case 4. 그 외 최종 폴백
    @ExceptionHandler(Exception.class)
    public ResponseEntity<CommonApiResponse<?>> handleUnexpectedException(Exception e) {
        log.error("서버 내부에 예상치 못한 에러가 발생했습니다.", e);

        ErrorCode errorCode = CommonErrorCode.INTERNAL_ERROR;

        return ResponseEntity
                .status(errorCode.getHttpStatus())
                .body(CommonApiResponse.error(errorCode));
    }
}
