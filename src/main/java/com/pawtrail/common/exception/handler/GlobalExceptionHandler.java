package com.pawtrail.common.exception.handler;

import com.pawtrail.common.exception.CommonErrorCode;
import com.pawtrail.common.exception.CustomException;
import com.pawtrail.common.exception.ErrorCode;
import com.pawtrail.common.response.CommonApiResponse;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
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

    // Case 5. 요청 본문을 읽을 수 없음
    //
    // 깨진 JSON 이거나 UUID 자리에 UUID 가 아닌 문자열이 들어오는 등
    // 본문을 요청 객체로 바꾸지 못하면 스프링이 HttpMessageNotReadableException 을 던짐
    // 보낸 쪽이 틀린 것이라 400 이 맞음
    // 이 핸들러가 없던 동안에는 최종 폴백이 잡아 500 이 나갔음
    //
    // * 가리킬 필드가 없어 field 를 "body" 로 둠
    //   검증 실패는 data 에 필드별 목록을 싣는다는 응답 규약을 그대로 따르기 위함임
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<CommonApiResponse<?>> handleMessageNotReadableException(HttpMessageNotReadableException e) {
        log.warn("요청 본문을 읽지 못했습니다: {}", e.getMostSpecificCause().getMessage());

        ErrorCode errorCode = CommonErrorCode.VALIDATION_FAILED;

        List<FieldErrorDetail> errorDetails =
                List.of(new FieldErrorDetail("body", "요청 본문을 읽을 수 없습니다. 형식을 확인해 주세요."));

        return ResponseEntity
                .status(errorCode.getHttpStatus())
                .body(CommonApiResponse.error(errorCode, errorDetails));
    }

    // Case 6. 주소는 있는데 그 요청 방식은 받지 않음
    //
    // GET 만 받는 주소에 DELETE 를 보내면 스프링이 HttpRequestMethodNotSupportedException 을 던짐
    // 이 핸들러가 없던 동안에는 최종 폴백이 잡아 405 대신 500 이 나갔음
    //
    // * Allow 헤더에 받는 방식을 담아 보냄
    //   HTTP 규약(RFC 9110)이 405 응답에는 그 헤더를 반드시 싣도록 정해 두었음
    //   스프링 기본 처리도 그렇게 하는데 이 핸들러가 가로채므로 여기서 직접 넣어야 함
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<CommonApiResponse<?>> handleMethodNotSupportedException(HttpRequestMethodNotSupportedException e) {
        log.warn("지원하지 않는 요청 방식입니다: {} (받는 방식: {})", e.getMethod(), e.getSupportedHttpMethods());

        ErrorCode errorCode = CommonErrorCode.METHOD_NOT_ALLOWED;

        ResponseEntity.BodyBuilder builder = ResponseEntity.status(errorCode.getHttpStatus());
        Set<HttpMethod> supported = e.getSupportedHttpMethods();
        if (supported != null && !supported.isEmpty()) {
            builder = builder.allow(supported.toArray(HttpMethod[]::new));
        }

        return builder.body(CommonApiResponse.error(errorCode));
    }

    // Case 7. 필수 쿼리 파라미터가 없음
    //
    // @RequestParam 이 받아야 할 값을 빼고 부르면 MissingServletRequestParameterException 이 남
    // 이 핸들러가 없던 동안에는 최종 폴백이 잡아 400 대신 500 이 나갔음
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<CommonApiResponse<?>> handleMissingParameterException(MissingServletRequestParameterException e) {
        log.warn("필수 파라미터가 없습니다: {}", e.getParameterName());

        ErrorCode errorCode = CommonErrorCode.VALIDATION_FAILED;

        List<FieldErrorDetail> errorDetails =
                List.of(new FieldErrorDetail(e.getParameterName(), "필수 값입니다."));

        return ResponseEntity
                .status(errorCode.getHttpStatus())
                .body(CommonApiResponse.error(errorCode, errorDetails));
    }

    // Case 8. 쿼리 파라미터 · 경로 변수의 제약 위반
    //
    // @Max · @Size 같은 제약을 요청 파라미터에 걸면 여기로 옴
    // 컨트롤러에 @Validated 가 붙어 있으면 AOP 가 ConstraintViolationException 을,
    // 안 붙어 있으면 스프링 MVC 가 HandlerMethodValidationException 을 던짐
    // 둘 다 이 핸들러가 없던 동안에는 최종 폴백이 잡아 400 대신 500 이 나갔음
    //
    // * 요청 본문(@Valid @RequestBody)의 필드 검증은 여기가 아니라 Case 2 로 감
    //
    // * jakarta.validation 을 직접 봄
    //   검증 스타터가 없는 서비스에서는 이 클래스를 읽지 못해 기동이 깨질 수 있음
    //   service-template 과 지금까지의 서비스 전부에 들어 있어 그 전제로 둠
    //   TraceIdResponseAdvice 가 Tracer 를 전제로 두는 것과 같은 판단임
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<CommonApiResponse<?>> handleConstraintViolationException(ConstraintViolationException e) {
        log.warn("파라미터 검증에 실패했습니다: {}", e.getMessage());

        ErrorCode errorCode = CommonErrorCode.VALIDATION_FAILED;

        List<FieldErrorDetail> errorDetails = e.getConstraintViolations().stream()
                .map(violation -> new FieldErrorDetail(
                        lastNodeName(violation.getPropertyPath()), violation.getMessage()))
                .toList();

        return ResponseEntity
                .status(errorCode.getHttpStatus())
                .body(CommonApiResponse.error(errorCode, errorDetails));
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<CommonApiResponse<?>> handleHandlerMethodValidationException(HandlerMethodValidationException e) {
        // 반환값 검증이 실패한 것은 서버가 틀린 응답을 만든 것이라 보낸 쪽 잘못이 아님
        // 스프링도 이 경우를 500 으로 보므로 최종 폴백에 맡김
        if (e.isForReturnValue()) {
            return handleUnexpectedException(e);
        }

        log.warn("파라미터 검증에 실패했습니다: {}", e.getMessage());

        ErrorCode errorCode = CommonErrorCode.VALIDATION_FAILED;

        List<FieldErrorDetail> errorDetails = new ArrayList<>();
        for (ParameterValidationResult result : e.getParameterValidationResults()) {
            String field = parameterName(result.getMethodParameter());
            for (MessageSourceResolvable error : result.getResolvableErrors()) {
                errorDetails.add(new FieldErrorDetail(field, error.getDefaultMessage()));
            }
        }

        return ResponseEntity
                .status(errorCode.getHttpStatus())
                .body(CommonApiResponse.error(errorCode, errorDetails));
    }

    // Case 9. 그 외 최종 폴백
    @ExceptionHandler(Exception.class)
    public ResponseEntity<CommonApiResponse<?>> handleUnexpectedException(Exception e) {
        log.error("서버 내부에 예상치 못한 에러가 발생했습니다.", e);

        ErrorCode errorCode = CommonErrorCode.INTERNAL_ERROR;

        return ResponseEntity
                .status(errorCode.getHttpStatus())
                .body(CommonApiResponse.error(errorCode));
    }

    // 제약 위반 경로의 마지막 이름을 field 로 씀
    //
    // @Validated 컨트롤러면 경로가 "메서드명.파라미터명" 이라 앞부분을 떼야 함
    // 요청 본문 검증이 필드 이름만 내보내는 것과 모양을 맞추기 위함임
    private static String lastNodeName(Path path) {
        String name = null;
        for (Path.Node node : path) {
            name = node.getName();
        }
        return name;
    }

    // 파라미터가 요청에서 불리는 이름을 돌려줌
    //
    // @RequestParam("ids") 처럼 이름을 따로 줬으면 그것이 요청에 보이는 이름이고
    // 자바 파라미터 이름은 그와 다를 수 있어 애노테이션을 먼저 봄
    // name 과 value 는 서로 별칭이라 한쪽만 채워져 있을 수 있어 둘 다 봄
    private static String parameterName(MethodParameter parameter) {
        RequestParam requestParam = parameter.getParameterAnnotation(RequestParam.class);
        if (requestParam != null) {
            String name = requestParam.name().isEmpty() ? requestParam.value() : requestParam.name();
            if (!name.isEmpty()) {
                return name;
            }
        }

        PathVariable pathVariable = parameter.getParameterAnnotation(PathVariable.class);
        if (pathVariable != null) {
            String name = pathVariable.name().isEmpty() ? pathVariable.value() : pathVariable.name();
            if (!name.isEmpty()) {
                return name;
            }
        }

        return parameter.getParameterName();
    }
}
