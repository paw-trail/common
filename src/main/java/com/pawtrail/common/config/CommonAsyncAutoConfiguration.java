package com.pawtrail.common.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * 애플리케이션 전역 비동기 실행 활성화
 *
 * 조건을 걸지 않은 이유는 OutboxCommitListener 말고도 verdict가 policy·pet을 병렬 호출하는 등
 * Outbox와 무관한 @Async 사용처가 있기 때문
 * 메시징 설정에 묶어두면 Outbox가 켜질 때만 비동기가 되는 결합이 생김
 *
 * 실행자 Bean은 따로 만들지 않음
 * Boot의 applicationTaskExecutor가 ThreadPoolTaskExecutor이고 spring.task.execution.pool.*로
 * 서비스마다 다르게 조절 가능하며, 여기서 Executor를 정의하면 TaskExecutionAutoConfiguration과 충돌 가능
 */
@AutoConfiguration
@EnableAsync
public class CommonAsyncAutoConfiguration {
}
