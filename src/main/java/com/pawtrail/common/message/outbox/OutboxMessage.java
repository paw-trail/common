package com.pawtrail.common.message.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "outbox")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
// Outbox는 Business Entity가 아니므로 BaseEntity를 상속하지 않는다
public class OutboxMessage {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(nullable = false, unique = true, columnDefinition = "uuid")
    private UUID eventId;

    @Column(nullable = false, length = 50)
    private String aggregateType;

    @Column(nullable = false, length = 64)
    private String aggregateId;

    @Column(nullable = false, length = 50)
    private String topic;

    // String으로 payload를 보관하여 발행 시 재직렬화 없이 바로 전송
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String payload;

    // 재시도 카운트 횟수
    @Column(nullable = false)
    private int retryCount;

    @Column(columnDefinition = "text")
    private String lastError;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime publishedAt;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }

    public static OutboxMessage create(
            UUID eventId,
            String aggregateType,
            String aggregateId,
            String topic,
            String payload
    ) {
        OutboxMessage message = new OutboxMessage();
        message.eventId = eventId;
        message.aggregateType = aggregateType;
        message.aggregateId = aggregateId;
        message.topic = topic;
        message.payload = payload;
        message.retryCount = 0;
        message.createdAt = LocalDateTime.now();
        return message;
    }

    // 메시지 발행 성공
    // lastError를 남겨두어 나중에 장애 조사를 가능토록 함
    public void markPublished() {
        this.publishedAt = LocalDateTime.now();
    }

    // 메시지 발행 실패
    public void recordFailure(String errorMessage) {
        this.retryCount++;
        this.lastError = errorMessage;
    }

    // 발행 여부 확인
    public boolean isPublished() {
        return this.publishedAt != null;
    }
}
