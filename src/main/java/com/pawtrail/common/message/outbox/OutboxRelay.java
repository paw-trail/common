package com.pawtrail.common.message.outbox;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// 발행에 실패한 미발행 메시지를 재시도
// 동일 서비스에 대한 여러 인스터스 중에 한 인스터스만 (app.outbox.relay.enabled=true)를 통해 실행
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnClass(name = "org.springframework.data.jpa.repository.JpaRepository")
@ConditionalOnProperty(name = "app.outbox.relay.enabled", havingValue = "true")
public class OutboxRelay {

    private final OutboxRepository outboxRepository;
    private final OutboxPublisher outboxPublisher;

    // 한 주기당 처리할 최대 건수, 건당 최대 3초 대기하므로 크게 잡지 않음
    private static final int BATCH_SIZE = 20;

    // 5초마다 Relay 작동
    @Scheduled(fixedDelayString = "${app.outbox.relay.interval-ms:5000}")
    @Transactional(readOnly = true)
    public void publishPendingMessages() {
        // 부분 인덱스(idx_outbox_unpublished)를 통해 오래된 순으로 미발행 건을 조회
        List<OutboxMessage> pendingMessages =
                outboxRepository.findUnpublishedMessages(PageRequest.of(0, BATCH_SIZE));

        if (pendingMessages.isEmpty()) {
            return;
        }

        // 미발행 메시지가 있다면 재발행 시도
        log.info("미발행 Outbox 메시지 {}건 발견. 회수 발행을 진행합니다.", pendingMessages.size());

        for (OutboxMessage message : pendingMessages) {
            // 같은 집합체에 더 오래된 미발행 건이 있으면 순서가 역전되므로 건너뛰고 기다림
            boolean hasOlderPending = outboxRepository.existsOlderUnpublishedMessage(
                    message.getAggregateType(),
                    message.getAggregateId(),
                    message.getCreatedAt()
            );

            if (hasOlderPending) {
                log.debug("선행 미발행 이벤트가 있어 발행을 연기합니다: eventId={}, aggregateId={}",
                        message.getEventId(), message.getAggregateId());
                continue;
            }

            // 건당 독립 트랜잭션으로 발행
            outboxPublisher.publish(message.getId());
        }
    }
}
