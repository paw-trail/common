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
import org.springframework.web.servlet.resource.NoResourceFoundException;

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

    // Case 4. 없는 경로
    //
    // 컨트롤러가 없는 주소를 부르면 스프링이 정적 리소스를 찾아보고
    // 그것도 없으면 NoResourceFoundException 을 던짐
    // 아래 최종 폴백이 잡으면 500 이 나가는데 그것은 서버 오류가 아니라
    // "그런 주소가 없다" 는 뜻이므로 404 로 바꿔 내보냄
    //
    // * 로그를 warn 으로 두는 것도 함께임
    //   최종 폴백은 error 로 스택트레이스를 찍는데,
    //   오타 난 주소 하나에 에러 로그가 쌓이면 진짜 오류를 찾기 어려워짐
    //
    // * 이 예외를 여기서 받으려면 스프링이 그것을 던지게 두어야 함
    //   spring.web.resources.add-mappings 를 끄면 이 예외 대신 다른 경로로 흐름
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<CommonApiResponse<?>> handleNoResourceFoundException(NoResourceFoundException e) {
        log.warn("존재하지 않는 경로 요청: {} {}", e.getHttpMethod(), e.getResourcePath());

        ErrorCode errorCode = CommonErrorCode.RESOURCE_NOT_FOUND;

        return ResponseEntity
                .status(errorCode.getHttpStatus())
                .body(CommonApiResponse.error(errorCode));
    }

    // Case 5. 그 외 최종 폴백
    @ExceptionHandler(Exception.class)
    public ResponseEntity<CommonApiResponse<?>> handleUnexpectedException(Exception e) {
        log.error("서버 내부에 예상치 못한 에러가 발생했습니다.", e);

        ErrorCode errorCode = CommonErrorCode.INTERNAL_ERROR;

        return ResponseEntity
                .status(errorCode.getHttpStatus())
                .body(CommonApiResponse.error(errorCode));
    }
}
