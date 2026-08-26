package com.pawtrail.common.message;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.TopicPartition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

/**
 * Consumer 예외 처리 정책
 *
 * GlobalExceptionHandler는 HTTP 요청 스레드 밖 예외를 잡지 못하므로,
 * KafkaListener에서 터진 예외는 여기서 처리하지 않으면 스프링 카프카 기본 동작으로
 * 같은 메시지를 무한 재시도하며 파티션이 막히게 됨
 *
 * 정책: 1초부터 2배씩 늘려 3회 재시도하고, 그래도 실패하면 dlq로 보낸 뒤 오프셋을 넘김
 */
@Slf4j
@Configuration
@ConditionalOnClass(name = "org.springframework.kafka.core.KafkaTemplate")
public class KafkaConsumerConfig {

    private static final long INITIAL_INTERVAL_MS = 1_000L;
    private static final double MULTIPLIER = 2.0;
    private static final long MAX_INTERVAL_MS = 4_000L;
    private static final int MAX_ATTEMPTS = 3;

    @Bean
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
