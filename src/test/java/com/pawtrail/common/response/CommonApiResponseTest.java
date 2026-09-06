package com.pawtrail.common.response;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * 응답 봉투를 다시 읽을 수 있는지 확인합니다.
 *
 * 이 클래스는 만드는 쪽과 읽는 쪽이 둘 다 씁니다.
 * 서비스가 응답을 만들 때 쓰고, 그 응답을 받는 다른 서비스가 같은 타입으로 읽습니다.
 * 만드는 길만 검증하면 읽는 길이 막힌 것을 알 수 없습니다.
 *
 * 실제로 막혀 있었습니다.
 * 생성자가 private 이고 @JsonCreator 가 없어
 * 받는 쪽이 예외로 실패했습니다.
 * 부르는 쪽이 그 예외를 삼키면 값만 조용히 비어 화면은 멀쩡해 보입니다.
 *
 * JsonMapper 를 씁니다. Jackson 3 의 매퍼이고 이 프로젝트가 실제로 쓰는 것입니다.
 * 클래스패스에 Jackson 2 도 함께 있어 그쪽 ObjectMapper 로도 컴파일은 되지만,
 * 그러면 실제로 쓰지 않는 매퍼를 검증하게 됩니다.
 * 애노테이션만은 Jackson 3 에서도 com.fasterxml.jackson.annotation 그대로입니다.
 *
 * 이 테스트가 하는 일은 하나입니다.
 * 나중에 누가 생성자를 고치거나 애노테이션을 지우면 여기서 걸립니다.
 */
class CommonApiResponseTest {

    private final JsonMapper jsonMapper = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    private record Sample(Long count) {
    }

    private record WithId(UUID accountId, Long count) {
    }

    @Test
    @DisplayName("만든 응답을 다시 같은 타입으로 읽을 수 있습니다")
    void 왕복() {
        CommonApiResponse<Sample> origin = CommonApiResponse.success(new Sample(7L));

        String json = jsonMapper.writeValueAsString(origin);
        CommonApiResponse<Sample> read = jsonMapper.readValue(
                json, new TypeReference<CommonApiResponse<Sample>>() {});

        assertThat(read.getCode()).isEqualTo("SUCCESS");
        assertThat(read.getData()).isNotNull();
        assertThat(read.getData().count()).isEqualTo(7L);
    }

    @Test
    @DisplayName("받는 쪽이 실제로 만나는 형태를 읽습니다")
    void 서비스가_보내는_형태() {
        // 다른 서비스가 내려보내는 그대로임
        // traceId 는 TraceIdResponseAdvice 가 응답 직전에 채움
        String json = """
                {
                  "code": "SUCCESS",
                  "message": "요청이 성공적으로 처리되었습니다.",
                  "data": { "count": 12 },
                  "traceId": "6a9d848ba6e5c03375608da479e2a685"
                }
                """;

        CommonApiResponse<Sample> read = jsonMapper.readValue(
                json, new TypeReference<CommonApiResponse<Sample>>() {});

        assertThat(read.getData().count()).isEqualTo(12L);
        assertThat(read.getTraceId()).isEqualTo("6a9d848ba6e5c03375608da479e2a685");
    }

    @Test
    @DisplayName("data 가 null 인 실패 응답도 읽힙니다")
    void 실패_응답() {
        String json = """
                {
                  "code": "RESOURCE_NOT_FOUND",
                  "message": "요청하신 경로를 찾을 수 없습니다.",
                  "data": null,
                  "traceId": null
                }
                """;

        CommonApiResponse<Sample> read = jsonMapper.readValue(
                json, new TypeReference<CommonApiResponse<Sample>>() {});

        assertThat(read.getCode()).isEqualTo("RESOURCE_NOT_FOUND");
        assertThat(read.getData()).isNull();
    }

    @Test
    @DisplayName("모르는 필드가 섞여 있어도 읽힙니다")
    void 모르는_필드() {
        // 받는 쪽 버전이 낮아 아직 모르는 칸이 있는 경우임
        // 여기서 막히면 필드를 더할 때마다 소비자가 전부 깨짐
        String json = """
                {
                  "code": "SUCCESS",
                  "message": "ok",
                  "data": { "count": 3, "unknownField": "x" },
                  "traceId": null,
                  "extra": 1
                }
                """;

        CommonApiResponse<Sample> read = jsonMapper.readValue(
                json, new TypeReference<CommonApiResponse<Sample>>() {});

        assertThat(read.getData().count()).isEqualTo(3L);
    }

    @Test
    @DisplayName("목록 응답도 읽힙니다")
    void 목록_응답() {
        // PageResponse 를 감싼 형태가 실제로 오가는 모양임
        String json = """
                {
                  "code": "SUCCESS",
                  "message": "ok",
                  "data": {
                    "content": [ { "count": 1 }, { "count": 2 } ],
                    "page": { "number": 0, "size": 20, "totalElements": 2, "totalPages": 1 }
                  },
                  "traceId": null
                }
                """;

        CommonApiResponse<PageResponse<Sample>> read = jsonMapper.readValue(
                json, new TypeReference<CommonApiResponse<PageResponse<Sample>>>() {});

        assertThat(read.getData().content()).hasSize(2);
        assertThat(read.getData().content().get(0).count()).isEqualTo(1L);
        assertThat(read.getData().page().totalElements()).isEqualTo(2L);
    }

    @Test
    @DisplayName("UUID 가 든 데이터도 읽힙니다")
    void uuid_데이터() {
        String json = """
                {
                  "code": "SUCCESS",
                  "message": "ok",
                  "data": {
                    "accountId": "01a046f8-93e7-75be-8000-000000000000",
                    "count": 5
                  },
                  "traceId": null
                }
                """;

        CommonApiResponse<WithId> read = jsonMapper.readValue(
                json, new TypeReference<CommonApiResponse<WithId>>() {});

        assertThat(read.getData().accountId())
                .isEqualTo(UUID.fromString("01a046f8-93e7-75be-8000-000000000000"));
        assertThat(read.getData().count()).isEqualTo(5L);
    }
}
