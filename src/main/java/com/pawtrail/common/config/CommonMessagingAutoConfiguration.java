package com.pawtrail.common.config;

import com.pawtrail.common.message.inbox.InboxProcessor;
import com.pawtrail.common.message.inbox.ProcessedEventRepository;
import com.pawtrail.common.message.outbox.OutboxCommitListener;
import com.pawtrail.common.message.outbox.OutboxPublisher;
import com.pawtrail.common.message.outbox.OutboxRelay;
import com.pawtrail.common.message.outbox.OutboxRepository;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Outbox 발행과 Inbox 멱등 처리 Bean 등록
 *
 * 조건이 JPA와 Kafka 둘 다인 이유는 OutboxPublisher가 KafkaTemplate을,
 * 나머지가 레포지터리를 주입받으므로 한쪽만 있는 서비스에서는 기동이 깨지기 때문
 *
 * EnableScheduling은 OutboxRelay 하나 때문에 필요하므로 여기에 둠
 * 발행하지 않는 서비스에서는 스케줄러가 아예 뜨지 않음
 */
@AutoConfiguration
@ConditionalOnClass(name = {
        "org.springframework.data.jpa.repository.JpaRepository",
        "org.springframework.kafka.core.KafkaTemplate"
})
@EnableScheduling
public class CommonMessagingAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public OutboxPublisher outboxPublisher(KafkaTemplate<String, Object> kafkaTemplate,
                                           OutboxRepository outboxRepository) {
        return new OutboxPublisher(kafkaTemplate, outboxRepository);
    }

    @Bean
    @ConditionalOnMissingBean
    public OutboxCommitListener outboxCommitListener(OutboxPublisher outboxPublisher) {
        return new OutboxCommitListener(outboxPublisher);
    }

    // 동일 서비스에 대한 여러 인스턴스 중에 한 인스턴스만 (app.outbox.relay.enabled=true)를 통해 실행
    // 여러 Relay가 동시에 돌면 둘 다 선행 미발행 건이 없다고 판단해 순서 보장이 깨짐
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(name = "app.outbox.relay.enabled", havingValue = "true")
    public OutboxRelay outboxRelay(OutboxRepository outboxRepository,
                                   OutboxPublisher outboxPublisher) {
        return new OutboxRelay(outboxRepository, outboxPublisher);
    }

    @Bean
    @ConditionalOnMissingBean
    public InboxProcessor inboxProcessor(ProcessedEventRepository processedEventRepository) {
        return new InboxProcessor(processedEventRepository);
    }
}
