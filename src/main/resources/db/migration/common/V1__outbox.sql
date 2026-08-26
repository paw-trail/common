-- 공통 마이그레이션 V1~V19 대역입니다. 서비스별 스크립트는 V20부터 사용합니다.
--
-- Outbox 패턴: 도메인 데이터 저장과 이벤트 발행을 한 트랜잭션으로 묶기 위한 테이블입니다.
-- 비즈니스 로직이 이 테이블에 행을 INSERT하고, 커밋 이후 OutboxCommitListener가 즉시
-- 발행을 시도하며, 놓친 행은 OutboxRelay가 폴링해 회수합니다.

CREATE TABLE outbox (
    -- 행 식별자. 애플리케이션이 UUID v7을 생성해 넣습니다.
    id uuid PRIMARY KEY,

    -- 이벤트 식별자. 소비자가 processed_event와 대조해 멱등성을 판단하는 키입니다.
    -- 같은 이벤트가 두 번 INSERT되는 것을 DB 차원에서 막습니다.
    event_id uuid NOT NULL UNIQUE,

    -- 집합체 타입(Place, Policy, Account 등)
    aggregate_type varchar(50) NOT NULL,

    -- 집합체 식별자. Kafka 파티션 키로 사용되어 같은 집합체의 이벤트 순서를 보장합니다.
    -- DomainEvent.getAggregateId()가 String을 반환하므로 uuid가 아닌 varchar입니다.
    aggregate_id varchar(64) NOT NULL,

    -- 발행 대상 토픽. 1 토픽 = 1 이벤트 타입 규약을 따릅니다.
    topic varchar(50) NOT NULL,

    -- EventEnvelope 전체를 직렬화해 담습니다(data만이 아니라 봉투째).
    -- 발행 시 그대로 꺼내 보내면 되므로 재조립이 필요 없습니다.
    payload jsonb NOT NULL,

    -- 국내 전용 서비스이므로 시간대 없는 timestamp를 사용하고 엔티티는 LocalDateTime으로 받습니다.
    -- 컨테이너에 TZ=Asia/Seoul이 설정돼 있어야 로컬과 배포의 시각이 일치합니다.
    created_at timestamp NOT NULL DEFAULT now(),

    -- NULL이면 미발행입니다. 발행 성공 시각을 기록합니다.
    published_at timestamp,

    -- 재시도 횟수. published_at만으로는 "한 번도 안 보냄"과 "여러 번 실패함"이
    -- 구분되지 않아 함께 둡니다.
    retry_count integer NOT NULL DEFAULT 0,

    -- 마지막 실패 사유. 재시도가 계속 실패할 때 원인 파악에 사용합니다.
    last_error text
);

-- 부분 인덱스: 미발행 행만 색인합니다.
-- OutboxRelay의 폴링 쿼리(WHERE published_at IS NULL ORDER BY created_at)가 사용하며,
-- 발행 완료 행이 아무리 쌓여도 인덱스 크기가 커지지 않습니다.
CREATE INDEX idx_outbox_unpublished
    ON outbox (created_at)
    WHERE published_at IS NULL;

-- 같은 집합체의 앞선 미발행 이벤트가 있는지 확인하는 순서 보장 로직용입니다.
-- (OutboxRelay가 A의 2번 이벤트를 1번보다 먼저 보내지 않도록)
CREATE INDEX idx_outbox_unpublished_aggregate
    ON outbox (aggregate_id, created_at)
    WHERE published_at IS NULL;

COMMENT ON TABLE outbox IS '도메인 이벤트 발행 대기함. 공통 모듈이 관리합니다.';
