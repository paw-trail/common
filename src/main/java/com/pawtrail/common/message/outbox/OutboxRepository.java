package com.pawtrail.common.message.outbox;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
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

    // 각 서비스의 관리자 재발행 API 가 쓰는 조회임
    // 위 두 메서드와 달리 retryCount 를 보지 않음
    //
    // * Relay 는 임계를 넘긴 건을 빼야 함
    //   포기한 건이 같은 집합체의 뒤 메시지를 영영 막기 때문임
    //
    // * 관리자는 반대로 그 임계를 넘긴 건을 봐야 함
    //   Relay 가 더 이상 줍지 않으므로 사람이 찾아내지 않으면 아무도 다시 보내지 않음
    //   에러도 남지 않아 조회 수단이 없으면 존재 자체를 알 수 없음
    //
    // * 여기서 찾은 건은 OutboxPublisher.publish 로 직접 발행함
    //   publish 에는 retryCount 검사가 없으므로 임계를 넘긴 건도 그대로 나감
    //   따라서 카운터를 되돌릴 필요가 없고, 남아 있는 값이 곧 "몇 번 실패했는지" 의 기록이 됨
    @Query("""
        SELECT m FROM OutboxMessage m
        WHERE m.publishedAt IS NULL
        ORDER BY m.createdAt ASC
    """)
    Page<OutboxMessage> findUnpublishedIgnoringRetryLimit(Pageable pageable);
}
