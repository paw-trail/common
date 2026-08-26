-- 공통 마이그레이션 V1~V19 대역입니다. 서비스별 스크립트는 V20부터 사용합니다.
--
-- Inbox(멱등 처리 이력): Kafka는 at-least-once라 같은 메시지가 두 번 이상 도착할 수 있습니다.
-- 소비자는 처리 직전 이 테이블에 event_id를 INSERT하고, PK 충돌이 나면 이미 처리한
-- 이벤트이므로 건너뜁니다. 별도 조회 없이 DB 제약이 멱등성을 보장합니다.
--
-- INSERT와 비즈니스 로직은 반드시 한 트랜잭션이어야 합니다.
-- 따로 커밋하면 "로직은 성공했는데 기록 실패"(재시도 시 중복 처리) 또는
-- "기록은 됐는데 로직 실패"(무한 건너뜀)가 발생합니다.

CREATE TABLE processed_event (
    -- 발행자가 EventEnvelope에 담아 보낸 eventId를 그대로 사용합니다.
    event_id uuid PRIMARY KEY,

    -- 어느 토픽에서 온 이벤트인지. 장애 조사와 정리 배치의 범위 지정에 사용합니다.
    topic varchar(50) NOT NULL,

    -- 국내 전용 서비스이므로 시간대 없는 timestamp를 사용합니다.
    processed_at timestamp NOT NULL DEFAULT now()
);

-- 오래된 처리 이력을 정리하는 배치가 생길 때 사용합니다.
-- (DELETE FROM processed_event WHERE processed_at < ?)
-- 지금은 정리 배치가 없으나 나중에 추가하면 공통 마이그레이션 번호를 또 써야 하므로
-- 인덱스만 미리 만들어 둡니다.
CREATE INDEX idx_processed_event_processed_at
    ON processed_event (processed_at);

COMMENT ON TABLE processed_event IS 'Kafka 이벤트 멱등 처리 이력. 공통 모듈이 관리합니다.';
