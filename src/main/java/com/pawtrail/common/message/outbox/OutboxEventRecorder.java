package com.pawtrail.common.message.outbox;

import com.pawtrail.common.message.DomainEvent;
import com.pawtrail.common.message.EventEnvelope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.json.JsonMapper;

// 서비스가 이벤트를 발행할 때 호출하는 입구
// 봉투 생성 → 직렬화 → outbox 행 저장 → 커밋 후 발행 신호까지를 한 번에 처리
//
// 이 클래스가 없으면 13개 서비스가 매번 같은 4단계를 손으로 반복해야 하고,
// 특히 마지막 이벤트 발행을 빠뜨리면 에러 없이 즉시 발행만 건너뛰어
// Relay 폴링 주기만큼 지연되므로 알아채기 어려움
@Slf4j
@RequiredArgsConstructor
public class OutboxEventRecorder {

    private final OutboxRepository outboxRepository;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final JsonMapper jsonMapper;

    // MANDATORY인 이유는 호출자에게 트랜잭션이 없으면 outbox 행이 별도 트랜잭션으로 새어
    // "비즈니스 데이터와 이벤트가 함께 저장된다"는 Outbox의 전제가 깨지기 때문
    // 이 경우 조용히 넘어가지 않고 IllegalTransactionStateException으로 즉시 터짐
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(DomainEvent event) {

        // 봉투 생성, eventId(UUID v7)와 occurredAt이 여기서 채워짐
        EventEnvelope<? extends DomainEvent> envelope = EventEnvelope.of(event);

        // 봉투 전체를 문자열로 보관해 발행 시 재직렬화가 필요 없게 함
        String payload = jsonMapper.writeValueAsString(envelope);

        OutboxMessage message = OutboxMessage.create(
                envelope.eventId(),
                envelope.aggregateType(),
                envelope.aggregateId(),
                envelope.eventType(),
                payload
        );

        // 비즈니스 데이터와 같은 트랜잭션으로 저장되므로 둘 다 되거나 둘 다 안 됨
        OutboxMessage saved = outboxRepository.save(message);

        // 커밋 직후 OutboxCommitListener가 받아 즉시 발행을 시도
        // 이 호출이 없으면 Relay가 주울 때까지 지연됨
        applicationEventPublisher.publishEvent(saved);

        log.debug("Outbox 기록: eventId={}, topic={}", saved.getEventId(), saved.getTopic());
    }
}
