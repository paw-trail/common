package com.pawtrail.common.message;

import com.pawtrail.common.enums.Role;
import com.pawtrail.common.security.principal.CustomUserPrincipal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.kafka.listener.RecordInterceptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

// 소비 시 Kafka 헤더를 SecurityContext로 복원
// Consumer는 HTTP 요청 밖 스레드에서 실행돼 SecurityContext가 비어 있으므로,
// 복원하지 않으면 Consumer가 만든 엔티티의 createdBy가 전부 SYSTEM 폴백으로 남음
//
// 발행 쪽 인터셉터는 만들지 않음 — Outbox를 쓰므로 발행하는 코드가 OutboxPublisher
// 한 곳뿐이고, 거기는 스케줄러/비동기 스레드라 SecurityContext가 이미 비어 있음
// 따라서 현재는 헤더를 넣는 주체가 없어 아래 첫 분기로 빠져나감
// 나중에 발행 쪽에서 헤더를 싣기 시작하면 이 코드를 고치지 않아도 그대로 동작함
//
// traceparent는 다루지 않음, 스프링 카프카 Observation이 처리하며,
// 직접 traceparent를 넣으면 헤더가 중복돼 표준상 무효 처리되어 받는 쪽이 새 trace를 시작함
//
// 제네릭이 <Object, Object>인 이유: Boot의 KafkaAnnotationDrivenConfiguration이
// ObjectProvider<RecordInterceptor<Object, Object>>로 이 빈을 찾음
// 제네릭은 불변이라 <String, Object>로 두면 빈은 뜨지만 리스너 컨테이너에 조용히 안 붙음
// 우리는 헤더만 읽으므로 키·값 타입에 의존하지 않아 <Object, Object>로 둬도 무방함
@Slf4j
@Component
@ConditionalOnClass(name = "org.springframework.kafka.core.KafkaTemplate")
public class KafkaSecurityInterceptor implements RecordInterceptor<Object, Object> {

    @Override
    public ConsumerRecord<Object, Object> intercept(ConsumerRecord<Object, Object> record,
                                                    Consumer<Object, Object> consumer) {

        String userId = headerValue(record, AuthContextHeaders.USER_ID);
        String userRole = headerValue(record, AuthContextHeaders.USER_ROLE);

        // 배치나 시스템이 발행해 인증 정보가 없는 경우
        // 이전 메시지의 컨텍스트가 남아있을 수 있으므로 비우고 통과
        if (userId == null || userRole == null) {
            SecurityContextHolder.clearContext();
            return record;
        }

        try {
            CustomUserPrincipal principal =
                    new CustomUserPrincipal(UUID.fromString(userId), Role.valueOf(userRole));

            List<GrantedAuthority> authorities =
                    List.of(new SimpleGrantedAuthority("ROLE_" + userRole));

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(principal, null, authorities);

            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);

        } catch (IllegalArgumentException e) {
            // UUID 파싱 실패 또는 알 수 없는 Role
            log.warn("유효하지 않은 인증 헤더 형식입니다. userId={}, userRole={}", userId, userRole);
            SecurityContextHolder.clearContext();
        }

        return record;
    }

    // 처리가 끝나면 Consumer 스레드가 재사용되므로 반드시 비움
    // 안 지우면 인증 정보 없는 다음 메시지가 앞 사용자의 컨텍스트를 물려받음
    // (HTTP 필터에서는 FilterChainProxy가 대신 해주지만 여기서는 직접 해야 함)
    @Override
    public void success(ConsumerRecord<Object, Object> record, Consumer<Object, Object> consumer) {
        SecurityContextHolder.clearContext();
    }

    @Override
    public void failure(ConsumerRecord<Object, Object> record, Exception exception,
                        Consumer<Object, Object> consumer) {
        SecurityContextHolder.clearContext();
    }

    private String headerValue(ConsumerRecord<Object, Object> record, String key) {
        Header header = record.headers().lastHeader(key);
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }
}
