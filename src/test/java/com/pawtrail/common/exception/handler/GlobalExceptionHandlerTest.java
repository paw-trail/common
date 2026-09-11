package com.pawtrail.common.exception.handler;

import static org.assertj.core.api.Assertions.assertThat;

import com.pawtrail.common.exception.handler.GlobalExceptionHandler.FieldErrorDetail;
import com.pawtrail.common.response.CommonApiResponse;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Size;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.validation.beanvalidation.MethodValidationAdapter;
import org.springframework.validation.method.MethodValidationResult;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

/**
 * 잘못된 요청이 500 이 아니라 4xx 로 나가는지 확인합니다.
 *
 * 여기서 다루는 예외 넷은 전부 보낸 쪽이 틀린 경우입니다.
 * 전용 핸들러가 없던 동안에는 최종 폴백이 잡아 500 을 냈고,
 * 그러면 서버 오류로 집계되어 로그와 모니터링에서 진짜 장애와 섞였습니다.
 *
 * 핸들러 메서드를 직접 부릅니다.
 * 스프링을 띄우지 않고도 응답의 상태 코드와 본문 모양을 볼 수 있고,
 * 어느 핸들러로 가는지는 예외 타입이 정하므로 따로 볼 것이 없습니다.
 *
 * 예외는 가능한 한 실제로 만들어지는 길로 만듭니다.
 * 파라미터 검증 예외는 검증기를 실제로 돌려 얻습니다.
 * 손으로 흉내 내면 필드 이름이 어디서 오는지를 검증하지 못합니다.
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("본문을 읽을 수 없으면 400 이고 field 가 body 입니다")
    void 본문을_읽을_수_없음() {
        HttpMessageNotReadableException e = new HttpMessageNotReadableException(
                "JSON parse error", new MockHttpInputMessage(new byte[0]));

        ResponseEntity<CommonApiResponse<?>> response = handler.handleMessageNotReadableException(e);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getCode()).isEqualTo("VALIDATION_FAILED");
        assertThat(details(response)).extracting(FieldErrorDetail::field).containsExactly("body");
    }

    @Test
    @DisplayName("받지 않는 요청 방식이면 405 이고 Allow 헤더에 받는 방식이 실립니다")
    void 받지_않는_요청_방식() {
        HttpRequestMethodNotSupportedException e =
                new HttpRequestMethodNotSupportedException("DELETE", List.of("GET"));

        ResponseEntity<CommonApiResponse<?>> response = handler.handleMethodNotSupportedException(e);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(response.getBody().getCode()).isEqualTo("METHOD_NOT_ALLOWED");
        assertThat(response.getBody().getData()).isNull();
        assertThat(response.getHeaders().getAllow()).containsExactly(HttpMethod.GET);
    }

    @Test
    @DisplayName("필수 파라미터가 없으면 400 이고 field 가 그 파라미터 이름입니다")
    void 필수_파라미터_없음() {
        MissingServletRequestParameterException e =
                new MissingServletRequestParameterException("ids", "List");

        ResponseEntity<CommonApiResponse<?>> response = handler.handleMissingParameterException(e);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getCode()).isEqualTo("VALIDATION_FAILED");
        assertThat(details(response)).extracting(FieldErrorDetail::field).containsExactly("ids");
    }

    @Test
    @DisplayName("@Validated 컨트롤러의 제약 위반은 400 이고 field 가 경로의 마지막 이름입니다")
    void 제약_위반() {
        ConstraintViolationException e;
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            Validator validator = factory.getValidator();
            Set<ConstraintViolation<SizeHolder>> violations = validator.validate(new SizeHolder(21));
            e = new ConstraintViolationException(violations);
        }

        ResponseEntity<CommonApiResponse<?>> response = handler.handleConstraintViolationException(e);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getCode()).isEqualTo("VALIDATION_FAILED");
        assertThat(details(response)).extracting(FieldErrorDetail::field).containsExactly("size");
    }

    @Test
    @DisplayName("스프링 MVC 의 파라미터 검증 실패는 400 이고 field 가 요청 파라미터 이름입니다")
    void 파라미터_검증_실패() throws NoSuchMethodException {
        Method method = SampleController.class.getMethod("find", List.class);
        MethodValidationResult result = new MethodValidationAdapter().validateArguments(
                new SampleController(), method, null,
                new Object[] {List.of("a", "b", "c")}, new Class<?>[0]);

        ResponseEntity<CommonApiResponse<?>> response =
                handler.handleHandlerMethodValidationException(new HandlerMethodValidationException(result));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getCode()).isEqualTo("VALIDATION_FAILED");
        assertThat(details(response)).extracting(FieldErrorDetail::field).containsExactly("ids");
    }

    @Test
    @DisplayName("반환값 검증 실패는 보낸 쪽 잘못이 아니라서 500 입니다")
    void 반환값_검증_실패() throws NoSuchMethodException {
        Method method = SampleController.class.getMethod("list");
        MethodValidationResult result = new MethodValidationAdapter().validateReturnValue(
                new SampleController(), method, null, List.of("a", "b"), new Class<?>[0]);

        ResponseEntity<CommonApiResponse<?>> response =
                handler.handleHandlerMethodValidationException(new HandlerMethodValidationException(result));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().getCode()).isEqualTo("INTERNAL_ERROR");
    }

    // 검증 실패 응답의 data 는 필드별 목록임
    @SuppressWarnings("unchecked")
    private static List<FieldErrorDetail> details(ResponseEntity<CommonApiResponse<?>> response) {
        return (List<FieldErrorDetail>) response.getBody().getData();
    }

    // 필드 제약 하나를 가진 검증 대상임
    static class SizeHolder {

        @Max(20)
        private final int size;

        SizeHolder(int size) {
            this.size = size;
        }
    }

    // 스프링 MVC 가 검증하는 컨트롤러 메서드를 흉내 냄
    //
    // 요청 파라미터 이름(ids)과 자바 파라미터 이름(placeIds)을 일부러 다르게 둠
    // 핸들러가 자바 파라미터 이름보다 애노테이션의 이름을 먼저 보는지 확인하기 위함임
    public static class SampleController {

        public void find(@RequestParam("ids") @Size(max = 2) List<String> placeIds) {
        }

        @Size(max = 1)
        public List<String> list() {
            return List.of();
        }
    }
}
