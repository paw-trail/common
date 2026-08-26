package com.pawtrail.common.message;

import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.UUID;

// 표준화된 메시지 전송 규격(양식), T는 payload
public record EventEnvelope<T>(
        UUID eventId,
        String eventType,
        LocalDateTime occurredAt,
        String aggregateType,
        String aggregateId,
        T data
) {

    private static final SecureRandom RANDOM = new SecureRandom();

    // 메시지 표준화 규격에 맞춰 표준 EventEnvelope 생성
    public static <T extends DomainEvent> EventEnvelope<T> of(T event) {
        return new EventEnvelope<>(
                generateUuidV7(),
                event.getTopic(),
                LocalDateTime.now(),
                event.getAggregateType(),
                event.getAggregateId(),
                event
        );
    }

    // 무상태 서비스(verdict 등)에서는 hibernate를 안쓰니 해당 메서드로 사용
    // 외부 라이브러리 없이 Java21 표준 API로 생성하는 간이 UUID v7 생성기
    public static UUID generateUuidV7() {
        long epochMillis = System.currentTimeMillis();
        byte[] value = new byte[16];
        RANDOM.nextBytes(value);

        ByteBuffer buffer = ByteBuffer.wrap(value);
        // Timestamp (48 bits)
        buffer.putShort(0, (short) (epochMillis >>> 32));
        buffer.putInt(2, (int) epochMillis);

        // Version 7 (0111)
        value[6] = (byte) ((value[6] & 0x0F) | 0x70);
        // Variant RFC 4122 (10xx)
        value[8] = (byte) ((value[8] & 0x3F) | 0x80);

        long mostSigBits = ByteBuffer.wrap(value, 0, 8).getLong();
        long leastSigBits = ByteBuffer.wrap(value, 8, 8).getLong();

        return new UUID(mostSigBits, leastSigBits);
    }
}
