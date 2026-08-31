package com.pawtrail.common.message.outbox;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
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

    // 발행하기 직전에 그 행을 잠그고 가져옴
    //
    // * 왜 필요한가
    //   즉시 발행(OutboxCommitListener)과 회수 발행(OutboxRelay)이 같은 행을 동시에 집으면
    //   같은 이벤트가 카프카에 두 번 실림
    //   OutboxPublisher 가 publishedAt 을 확인하기는 하나, 확인하는 시점과
    //   발행이 끝나 표시를 남기는 시점 사이가 벌어져 있어 그 틈에 둘 다 통과함
    //   특히 첫 발행은 프로듀서를 만드느라 0.6초가량 걸려 잘 겹침
    //
    // * SKIP LOCKED 를 쓰는 이유
    //   그냥 FOR UPDATE 로 두면 뒤에 온 쪽이 앞선 발행이 끝날 때까지 기다렸다가
    //   잠금이 풀린 뒤 publishedAt 을 보고 물러남
    //   결과는 같지만 최대 3초를 붙잡고 있게 되며, 회수 발행이 한 번에 20건을 도는 동안
    //   그런 대기가 쌓임
    //   SKIP LOCKED 는 남이 잡고 있는 행을 아예 건너뛰어 기다림이 생기지 않음
    //
    // * 인스턴스가 여러 개여도 그대로 동작함
    //   잠금이 데이터베이스에 있으므로 서로 다른 인스턴스의 발행도 겹치지 않음
    //   verdict 와 search 를 여러 개로 띄우기로 한 결정과 짝이 되는 자리임
    //
    // * JPA 의 @Lock 대신 네이티브 질의를 쓰는 이유
    //   SKIP LOCKED 를 JPA 로 지정하려면 잠금 대기 시간 힌트에 -2 라는 약속된 값을 넣어야 하는데,
    //   그 값의 뜻이 명세가 아니라 구현에 달려 있고 힌트가 질의보다 늦게 적용되는 문제가 보고돼 있음
    //   PostgreSQL 문법을 그대로 적으면 무엇을 하려는지가 코드에 드러나고 판올림에도 덜 흔들림
    @Query(value = """
        SELECT * FROM outbox
        WHERE id = :id
        FOR UPDATE SKIP LOCKED
    """, nativeQuery = true)
    Optional<OutboxMessage> findByIdForUpdateSkipLocked(@Param("id") UUID id);
}
