package com.pawtrail.common.audit;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
* 본 설정은 JPA를 사용하는 서비스에서만 활성화 된다.
* 조건 판단 기준을 EntityManager가 아닌 JpaRepository로 한 이유는
* hibernate-spatial이 무상태 서비스도 EntityManager 관련 클래스를 가져오기 때문이다.
*
* 만약 JPA가 없는 모듈에서 활성화되면 entityManagerFactory bean을 찾을 수 없어 실행이 불가하게 된다.
* */

@Configuration
@ConditionalOnClass(name = "org.springframework.data.jpa.repository.JpaRepository")
@EnableJpaAuditing(auditorAwareRef = "auditorProvider")
public class AuditConfig {

}
