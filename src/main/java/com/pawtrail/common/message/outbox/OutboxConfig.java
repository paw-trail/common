package com.pawtrail.common.message.outbox;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

// JPA 레포지터리를 사용하는 곳에서만 OutBox를 사용
@Configuration
@ConditionalOnClass(name = "org.springframework.data.jpa.repository.JpaRepository")
@EnableAsync
@EnableScheduling
public class OutboxConfig {
}
