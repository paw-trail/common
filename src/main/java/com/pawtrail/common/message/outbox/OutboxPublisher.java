package com.pawtrail.common.message.outbox;

import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

// Outbox 메시지를 발행하고 기록할 때 사용, Relay에서도 사용한다
// @Async와 @Transactional을 같은 메서드에 붙이지 않기 위해 분리함
// @Async와 @Transactional은 적용 순서가 보장되지 않아 @Transactional이 밖에 걸리면
// 호출 스레드에서 트랜잭션이 시작돼 비동기 스레드와 끊길 수 있기 때문
@Slf4j
@RequiredArgsConstructor
public class OutboxPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final OutboxRepository outboxRepository;
    private static final long PUBLISH_TIMEOUT_SECONDS = 3L;

    // 발행 성공 여부를 반환, Relay에서 다음 메시지를 처리할지 판단할 때 활용
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean publish(UUID outboxId) {

        // 호출자의 영속성 컨텍스트와 분리된 트랜잭션이므로 다시 조회
        //
        // 이때 행을 잠급니다.
        // 즉시 발행과 회수 발행이 같은 행을 동시에 집으면 같은 이벤트가 두 번 실리는데,
        // 아래 isPublished 검사만으로는 막지 못합니다.
        // 확인하는 시점과 발행이 끝나 표시를 남기는 시점 사이가 벌어져 있어
        // 그 틈에 들어온 쪽도 미발행으로 보기 때문입니다.
        //
        // 잠금은 이 트랜잭션이 끝날 때까지 유지되고 그 안에 카프카 전송이 들어 있으므로,
        // 전송이 끝나 표시가 남을 때까지 다른 경로가 이 행을 건드리지 못합니다.
        OutboxMessage message = outboxRepository.findByIdForUpdateSkipLocked(outboxId)
                .orElse(null);

        if (message == null) {
            // 두 가지 경우가 여기로 옵니다.
            //   ①다른 경로가 이미 잠그고 발행 중이다 - 정상이며 아무것도 하지 않는 것이 맞음
            //   ②그런 행이 없다 - 잘못된 호출이므로 알아야 함
            //
            // 잠긴 행은 건너뛰어져 조회 결과가 비므로 둘이 구분되지 않습니다.
            // 잠기지 않은 조회를 한 번 더 해서 가려냅니다.
            // 이 질의는 결과가 비었을 때만 실행되므로 평소 경로에는 부담이 없습니다.
            if (outboxRepository.existsById(outboxId)) {
                log.debug("다른 경로가 발행 중이므로 건너뜁니다: outboxId={}", outboxId);
            } else {
                log.warn("Outbox 메시지를 찾을 수 없습니다: outboxId={}", outboxId);
            }
            return false;
        }

        // 앞선 발행이 이미 끝난 뒤에 들어온 경우입니다.
        // 위 잠금이 동시에 들어온 것을 막고, 이 검사가 뒤늦게 들어온 것을 막습니다.
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
