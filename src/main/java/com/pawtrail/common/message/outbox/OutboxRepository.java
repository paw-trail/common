package com.pawtrail.common.message.outbox;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface OutboxRepository extends JpaRepository<OutboxMessage, UUID> {

    // 임계를 넘긴 메시지는 조회에서 제외
    int MAX_RETRY_COUNT = 10;

    // 부분 인덱스(idx_outbox_unpublished)를 활용하여 미발행 메시지를 생성 시간 오름차순으로 조회
    @Query("""
        SELECT m FROM OutboxMessage m
        WHERE m.publishedAt IS NULL
          AND m.retryCount < :maxRetryCount
        ORDER BY m.createdAt ASC
    """)
    List<OutboxMessage> findUnpublishedMessages(@Param("maxRetryCount") int maxRetryCount,
                                                Pageable pageable);

    // 동일 집합체(aggregate)에 대해 먼저 생성된 미발행 메시지가 있는지 확인
    // 여기에도 retryCount 조건이 있어야 함
    @Query("""
        SELECT COUNT(m) > 0 FROM OutboxMessage m
        WHERE m.aggregateType = :aggregateType
          AND m.aggregateId = :aggregateId
          AND m.publishedAt IS NULL
          AND m.retryCount < :maxRetryCount
          AND m.createdAt < :createdAt
    """)
    boolean existsOlderUnpublishedMessage(
            @Param("aggregateType") String aggregateType,
            @Param("aggregateId") String aggregateId,
            @Param("createdAt") LocalDateTime createdAt,
            @Param("maxRetryCount") int maxRetryCount
    );
}
