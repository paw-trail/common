package com.pawtrail.common.message.outbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

// Business 트랜잭션이 커밋된 직후 즉시 발행을 시도
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnClass(name = "org.springframework.data.jpa.repository.JpaRepository")
public class OutboxCommitListener {

    private final OutboxPublisher outboxPublisher;

    // 커밋 전에 발행하면 자칫 롤백된 트랜잭션에 대한 이벤트가 발행될 수 있음
    // 커밋이 끝나도 같은 스레드이면 HTTP 응답이 Kafka 발행을 기다릴 수 있기에 비동기
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCommit(OutboxMessage message) {
        log.debug("Outbox 즉시 발행 시도: eventId={}", message.getEventId());
        outboxPublisher.publish(message.getId());
    }
}
