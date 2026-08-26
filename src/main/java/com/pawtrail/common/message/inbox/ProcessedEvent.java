package com.pawtrail.common.message.inbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Persistable;

// 처리된 이벤트들을 기록, PK(UUID) 충돌이 멱등 장치이므로 조회가 필요 없음
// Outbox와 마찬가지로 시스템 테이블이라 BaseEntity를 상속하지 않음
//
// Persistable을 구현하는 이유: eventId는 발행자가 준 값이라 save() 시점에 이미 null이 아님
// 그대로 두면 SimpleJpaRepository가 isNew()=false로 보고 persist()가 아니라 merge()를 호출하고,
// merge는 SELECT 후 행이 있으면 UPDATE라서 PK 충돌이 나지 않음
// 그러면 다른 인스턴스가 먼저 커밋한 경쟁 상황에서 비즈니스 로직이 중복 실행됨
@Entity
@Table(name = "processed_event")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProcessedEvent implements Persistable<UUID> {

    // 발행자가 EventEnvelope에 담아 보낸 eventId를 그대로 사용
    @Id
    private UUID eventId;

    @Column(nullable = false, length = 50)
    private String topic;

    @Column(nullable = false, updatable = false)
    private LocalDateTime processedAt;

    public static ProcessedEvent of(UUID eventId, String topic) {
        ProcessedEvent processedEvent = new ProcessedEvent();
        processedEvent.eventId = eventId;
        processedEvent.topic = topic;
        processedEvent.processedAt = LocalDateTime.now();
        return processedEvent;
    }

    @Override
    public UUID getId() {
        return eventId;
    }

    // 항상 새 행으로 취급해 persist()가 나가게 함
    // 이미 있으면 PK 충돌이 나야 정상이고, 그게 이 테이블의 멱등 장치임
    @Override
    public boolean isNew() {
        return true;
    }
}
