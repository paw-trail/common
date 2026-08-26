package com.pawtrail.common.message.outbox;

import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

// Outbox 메시지를 발행하고 기록할 때 사용, Relay에서도 사용한다
// @Async와 @Transactional을 같은 메서드에 붙이지 않기 위해 분리함
// @Async와 @Transactional은 적용 순서가 보장되지 않아 @Transactional이 밖에 걸리면
// 호출 스레드에서 트랜잭션이 시작돼 비동기 스레드와 끊길 수 있기 때문
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnClass(name = "org.springframework.data.jpa.repository.JpaRepository")
public class OutboxPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final OutboxRepository outboxRepository;
    private static final long PUBLISH_TIMEOUT_SECONDS = 3L;

    // 발행 성공 여부를 반환, Relay에서 다음 메시지를 처리할지 판단할 때 활용
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean publish(UUID outboxId) {

        // 호출자의 영속성 컨텍스트와 분리된 트랜잭션이므로 다시 조회
        OutboxMessage message = outboxRepository.findById(outboxId).orElse(null);

        if (message == null) {
            log.warn("Outbox 메시지를 찾을 수 없습니다: outboxId={}", outboxId);
            return false;
        }

        // 즉시 발행과 회수 발행이 겹쳐 이미 처리됐을 수 있음
        if (message.isPublished()) {
            return true;
        }

        try {
            // aggregateId를 파티션 키로 지정해 동일 집합체의 순서를 보장
            kafkaTemplate.send(message.getTopic(), message.getAggregateId(), message.getPayload())
                    .get(PUBLISH_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            // 발행 성공
            message.markPublished();
            log.debug("Outbox 발행 성공: eventId={}", message.getEventId());
            return true;

        } catch (Exception e) {
            message.recordFailure(e.getMessage());

            if (message.getRetryCount() >= OutboxRepository.MAX_RETRY_COUNT) {
                // 이 시점부터 Relay 조회에서 빠지므로 알아야 함
                log.error("Outbox 최대 재시도 초과. 더 이상 발행하지 않습니다: eventId={}, topic={}, reason={}",
                        message.getEventId(), message.getTopic(), e.getMessage());
            } else {
                log.warn("Outbox 발행 실패: eventId={}, retryCount={}, reason={}",
                        message.getEventId(), message.getRetryCount(), e.getMessage());
            }
            return false;
        }
    }
}
