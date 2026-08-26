package com.pawtrail.common.message.inbox;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, UUID> {
    // existsById와 save만으로 충분해 메서드를 추가하지 않음
    // 멱등 판단은 PK 제약이 하고, eventId는 발행자가 만든 값을 그대로 PK로 씀
}
