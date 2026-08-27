package com.pawtrail.common.config;

import com.pawtrail.common.message.KafkaSecurityInterceptor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.TopicPartition;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.converter.RecordMessageConverter;
import org.springframework.kafka.support.converter.StringJacksonJsonMessageConverter;
import org.springframework.util.backoff.ExponentialBackOff;

import tools.jackson.databind.json.JsonMapper;

/**
 * Consumer 역직렬화·예외 처리 정책 및 인증 컨텍스트 복원 인터셉터 등록
 *
 * GlobalExceptionHandler는 HTTP 요청 스레드 밖 예외를 잡지 못하므로,
 * KafkaListener에서 터진 예외는 여기서 처리하지 않으면 스프링 카프카 기본 동작으로
 * 같은 메시지를 무한 재시도하며 파티션이 막히게 됨
 *
 * 정책: 1초부터 2배씩 늘려 3회 재시도하고, 그래도 실패하면 dlq로 보낸 뒤 오프셋을 넘김
 */
@Slf4j
@AutoConfiguration
@ConditionalOnClass(name = "org.springframework.kafka.core.KafkaTemplate")
public class CommonKafkaAutoConfiguration {

    private static final long INITIAL_INTERVAL_MS = 1_000L;
    private static final double MULTIPLIER = 2.0;
    private static final long MAX_INTERVAL_MS = 4_000L;
    private static final int MAX_ATTEMPTS = 3;

    /**
     * value-deserializer가 StringDeserializer라 리스너에 JSON 문자열이 도착하는데,
     * 이 컨버터가 @KafkaListener 파라미터에 선언된 타입을 읽어 그 타입으로 역직렬화함
     * 제네릭 안쪽까지 살아 있어 EventEnvelope<XxxMessage> 형태로 바로 받을 수 있음
     *
     * 발행 쪽 OutboxEventRecorder와 같은 JsonMapper를 넘기는 이유는
     * 다른 매퍼를 쓰면 봉투의 LocalDateTime 표현이 나갈 때와 들어올 때 어긋날 수 있기 때문
     *
     * ConditionalOnMissingBean이 중요함
     * Boot가 ObjectProvider의 getIfUnique()로 집어가므로 서비스가 자기 것을 정의해
     * 빈이 2개가 되면 어느 쪽도 적용되지 않고 역직렬화가 통째로 안 됨
     */
    @Bean
    @ConditionalOnMissingBean(RecordMessageConverter.class)
    public RecordMessageConverter kafkaRecordMessageConverter(JsonMapper jsonMapper) {
        return new StringJacksonJsonMessageConverter(jsonMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public KafkaSecurityInterceptor kafkaSecurityInterceptor() {
        return new KafkaSecurityInterceptor();
    }

    @Bean
    @ConditionalOnMissingBean(CommonErrorHandler.class)
    public DefaultErrorHandler kafkaErrorHandler(KafkaOperations<String, Object> kafkaOperations) {

        // 최종 실패 시 {원본토픽}.dlq로 보냄
        // 파티션 번호를 그대로 쓰지 않는 이유는 DLQ 토픽의 파티션 수가 다를 수 있기 때문
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaOperations,
                (record, exception) -> {
                    log.error("최대 재시도를 초과해 DLQ로 보냅니다: topic={}, offset={}, reason={}",
                            record.topic(), record.offset(), exception.getMessage());
                    return new TopicPartition(record.topic() + ".dlq", -1);
                });

        ExponentialBackOff backOff = new ExponentialBackOff(INITIAL_INTERVAL_MS, MULTIPLIER);
        backOff.setMaxInterval(MAX_INTERVAL_MS);
        backOff.setMaxAttempts(MAX_ATTEMPTS);

        return new DefaultErrorHandler(recoverer, backOff);
    }
}
