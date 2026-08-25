package com.pawtrail.template;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * 서비스 진입점입니다.
 *
 * 공통 모듈(com.pawtrail.common)을 함께 스캔하도록 지정합니다.
 * BaseEntity 와 Outbox·Inbox 엔티티가 공통 모듈에 있기 때문입니다.
 *
 * DB를 사용하지 않는 서비스(verdict, congestion)는
 * 아래 @EntityScan 과 @EnableJpaRepositories 두 줄과 해당 import 를 지웁니다.
 * 남겨두면 JPA 가 필수가 되어 기동에 실패합니다.
 *
 * 참고: @EntityScan 의 패키지는 Spring Boot 4 에서
 * org.springframework.boot.autoconfigure.domain 에서 현재 위치로 옮겨졌습니다.
 * 인터넷의 예제 코드는 대부분 옛 경로라 그대로 붙여넣으면 컴파일되지 않습니다.
 */
@SpringBootApplication(scanBasePackages = {"com.pawtrail.template", "com.pawtrail.common"})
@EntityScan(basePackages = {"com.pawtrail.template", "com.pawtrail.common"})
@EnableJpaRepositories(basePackages = {"com.pawtrail.template", "com.pawtrail.common"})
public class TemplateApplication {

    public static void main(String[] args) {
        SpringApplication.run(TemplateApplication.class, args);
    }
}
