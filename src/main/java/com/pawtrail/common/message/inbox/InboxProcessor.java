package com.pawtrail.common.message.inbox;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// kafka 이벤트를 중복 없이 한번만 처리하도록 함
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnClass(name = "org.springframework.data.jpa.repository.JpaRepository")
public class InboxProcessor {

    private final ProcessedEventRepository processedEventRepository;

    // Business 로직과 이력 저장을 한 트랜잭션에 묶어서 실행
    // 한 트랜잭션에 없으면 두 방향으로 깨짐
    //   로직 성공 + 기록 실패 → 재시도 때 같은 이벤트를 다시 처리 (중복)
    //   기록 성공 + 로직 실패 → 그 이벤트를 영영 건너뜀 (유실)
    // 여기서 발생한 예외는 잡지 않고 DefaultErrorHandler로 재시도하고 최종 실패시 DLQ로 보냄
    // 처리에 성공하면 true, 이미 처리한 이벤트라 건너뛰면 false
    @Transactional
    public boolean processOnce(UUID eventId, String topic, Runnable businessLogic) {
        if (processedEventRepository.existsById(eventId)) {
            log.debug("이미 처리한 이벤트입니다. 건너뜁니다: eventId={}, topic={}", eventId, topic);
            return false;
        }

        // 먼저 기록해 두면 같은 트랜잭션 안에서 중복 실행을 막을 수 있고,
        // 로직이 실패하면 INSERT도 함께 롤백되어 재시도 시 다시 처리
        processedEventRepository.save(ProcessedEvent.of(eventId, topic));

        businessLogic.run();

        log.debug("이벤트 처리 완료: eventId={}, topic={}", eventId, topic);
        return true;
    }
}
