# common

paw-trail 조직의 서비스들이 공통으로 사용하는 라이브러리입니다. 실행되는 애플리케이션이 아니라 jar로 묶여 GitHub Packages에 배포되며, 각 서비스가 의존성으로 내려받아 사용합니다.

여기에 넣는 것은 **기술적 관심사이면서 거의 바뀌지 않는 것**입니다. 도메인 지식, 이벤트 payload DTO, 도메인 엔티티는 넣지 않습니다. 공통 모듈을 고치면 이를 사용하는 모든 서비스가 버전을 올려야 하므로, "여러 곳에서 쓰인다"만으로는 부족하고 "앞으로 거의 바뀌지 않는다"까지 만족해야 합니다.

---

## 1. 제공하는 것

| 영역 | 제공하는 것 |
|---|---|
| 응답 | `CommonApiResponse`, `PageResponse`, traceId 자동 주입 |
| 예외 | `ErrorCode` 계약, `CommonErrorCode`, `CustomException`, 전역 예외 핸들러 |
| 인증 | 헤더 기반 인증 필터, `CustomUserPrincipal`, `@CurrentUser`, 기본 보안 체인 |
| 엔티티 | `BaseEntity`(감사 컬럼 6개), 감사자 판정 |
| 이벤트 | 이벤트 봉투, Outbox 발행, Inbox 멱등 처리, 메시지 역직렬화, Consumer 예외 정책 |
| 스키마 | `outbox`·`processed_event` 테이블 마이그레이션 |

---

## 2. 서비스에 붙이는 방법

### 2-1. 의존성

서비스의 `build.gradle`에 저장소와 의존성을 선언합니다. 자격증명은 커밋에 들어가지 않도록 환경변수로 주입합니다.

```groovy
repositories {
    mavenCentral()
    maven {
        url = uri("https://maven.pkg.github.com/paw-trail/common")
        credentials {
            username = System.getenv("GPR_USER")
            password = System.getenv("GPR_TOKEN")
        }
    }
}

dependencies {
    implementation "com.pawtrail:common:${commonVersion}"
}
```

버전은 `gradle.properties`의 `commonVersion`으로 관리합니다. 공통 모듈이 갱신되면 이 숫자만 바꿔 다시 빌드합니다.

### 2-2. 애플리케이션 클래스

공통 모듈은 **자동 설정으로 등록됩니다.** 의존성만 추가하면 조건에 맞는 Bean이 알아서 올라옵니다.

```java
@SpringBootApplication(scanBasePackages = "com.pawtrail.place")
@EntityScan(basePackages = { "com.pawtrail.place", "com.pawtrail.common" })
@EnableJpaRepositories(basePackages = { "com.pawtrail.place", "com.pawtrail.common" })
public class PlaceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PlaceApplication.class, args);
    }
}
```

세 애노테이션의 역할이 다르므로 주의합니다.

- **`scanBasePackages`에 `com.pawtrail.common`을 넣지 않습니다.** 자동 설정과 컴포넌트 스캔에 모두 걸리면 같은 설정이 두 번 등록되고, 조건 평가 순서가 깨져 의도와 다른 Bean이 올라갈 수 있습니다.
- **`@EntityScan`과 `@EnableJpaRepositories`에는 넣습니다.** `OutboxMessage`·`ProcessedEvent` 엔티티와 그 레포지터리는 자동 설정이 잡아주지 않기 때문입니다.

DB를 사용하지 않는 서비스(verdict, congestion)는 `@EntityScan`과 `@EnableJpaRepositories`를 **애노테이션과 import까지 함께 제거합니다.** 남겨두면 JPA가 필수가 되어 컴파일 단계에서 실패합니다.

### 2-3. Flyway 위치

공통 마이그레이션은 jar 안에 들어 있으므로 서비스의 `application.yml`에 두 위치를 모두 지정합니다.

```yaml
spring:
  flyway:
    locations:
      - classpath:db/migration/common
      - classpath:db/migration
```

---

## 3. 자동 설정

`config/` 패키지의 6개 클래스가 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`에 등록되어 있습니다.

| 클래스 | 켜지는 조건 | 등록하는 Bean |
|---|---|---|
| `CommonWebAutoConfiguration` | 서블릿 웹 + spring-webmvc | `GlobalExceptionHandler`, `TraceIdResponseAdvice` |
| `CommonSecurityAutoConfiguration` | 서블릿 웹 + spring-security | `SecurityFilterChain`, `CustomSecurityExceptionHandler` |
| `CommonJpaAutoConfiguration` | spring-data-jpa | `AuditorProvider`, JPA Auditing 활성화 |
| `CommonMessagingAutoConfiguration` | spring-data-jpa + spring-kafka | `OutboxEventRecorder`, `OutboxPublisher`, `OutboxCommitListener`, `OutboxRelay`, `InboxProcessor` |
| `CommonKafkaAutoConfiguration` | spring-kafka | `RecordMessageConverter`, `KafkaSecurityInterceptor`, `DefaultErrorHandler` |
| `CommonAsyncAutoConfiguration` | 없음 | `@EnableAsync` |

조건 판단에 `jakarta.persistence.EntityManager`가 아니라 `org.springframework.data.jpa.repository.JpaRepository`를 쓰는 이유는, `hibernate-spatial`이 `hibernate-core`를 거쳐 `jakarta.persistence-api`를 전이로 끌고 오기 때문입니다. JPA 스타터를 제거한 서비스에서도 `EntityManager`는 클래스패스에 남아 조건이 참이 되어버립니다.

모든 Bean에 `@ConditionalOnMissingBean`이 붙어 있으므로, 서비스가 같은 타입의 Bean을 직접 정의하면 공통 모듈 쪽이 물러납니다. 예를 들어 로그인 경로를 열어야 하는 서비스는 자체 `SecurityFilterChain`을 정의하면 됩니다.

---

## 4. 사용법

### 4-1. 응답

모든 API 응답은 `CommonApiResponse`로 감쌉니다.

```java
@GetMapping("/api/v1/places/{placeId}")
public CommonApiResponse<PlaceResponse> getPlace(@PathVariable UUID placeId) {
    return CommonApiResponse.success(placeService.getPlace(placeId));
}
```

```json
{ "code": "SUCCESS", "message": "...", "data": { ... }, "traceId": "..." }
```

`traceId`는 응답 직전에 자동으로 채워지므로 컨트롤러가 신경 쓸 필요가 없습니다. 성공 응답에도 실리며, 문의가 들어왔을 때 해당 요청을 분산 추적에서 바로 찾기 위한 값입니다.

목록 응답은 `PageResponse`를 씁니다. 엔티티를 그대로 노출하지 않도록 변환 함수를 함께 넘깁니다.

```java
Page<Place> page = placeRepository.findAll(pageable);
return CommonApiResponse.success(PageResponse.from(page, PlaceResponse::from));
```

### 4-2. 예외

서비스마다 자기 `ErrorCode` enum을 만들어 구현합니다. **enum 상수 이름이 그대로 응답의 `code`가 되며 API 계약의 일부입니다.** 이름을 바꾸면 컴파일러가 잡아주지 않는 계약 변경이 되므로 주의합니다.

```java
@Getter
@RequiredArgsConstructor
public enum PlaceErrorCode implements ErrorCode {

    PLACE_NOT_FOUND(HttpStatus.NOT_FOUND, "장소를 찾을 수 없습니다."),
    PLACE_ALREADY_CLOSED(HttpStatus.CONFLICT, "이미 폐업 처리된 장소입니다.");

    private final HttpStatus httpStatus;
    private final String message;

    @Override
    public String getCode() {
        return name();
    }
}
```

던질 때는 `CustomException` 하나만 씁니다. 예외 클래스를 상태별로 나누지 않는 이유는 HTTP 상태가 이미 `ErrorCode`에 있어, 클래스가 두 번째 진실의 원천이 되면 둘이 어긋나도 알아채기 어렵기 때문입니다.

```java
throw new CustomException(PlaceErrorCode.PLACE_NOT_FOUND);
```

전 서비스에서 같은 뜻으로 쓰이는 코드만 `CommonErrorCode`에 있습니다. 도메인 개념이 들어간 코드는 넣지 않습니다.

`VALIDATION_FAILED` / `AUTHENTICATION_FAILED` / `ACCESS_DENIED` / `INTERNAL_ERROR` / `EXTERNAL_API_ERROR`

### 4-3. 인증

게이트웨이가 검증한 뒤 넣어주는 `X-User-Id`·`X-User-Role` 헤더를 필터가 읽어 `SecurityContext`를 채웁니다. 서비스는 토큰을 직접 다루지 않습니다.

```java
@GetMapping("/api/v1/pets")
public CommonApiResponse<List<PetResponse>> getMyPets(@CurrentUser CustomUserPrincipal principal) {
    return CommonApiResponse.success(petService.findByAccount(principal.accountId()));
}
```

`CustomUserPrincipal`은 `accountId(UUID)`와 `role(Role)` 둘만 가집니다. 기본 보안 체인은 `/internal/**`과 `/actuator/**`를 열고 나머지는 인증을 요구합니다.

### 4-4. 엔티티

모든 도메인 엔티티는 `BaseEntity`를 상속합니다. 생성·수정 컬럼은 JPA Auditing이 자동으로 채웁니다.

```java
@Entity
@Table(name = "place")
public class Place extends BaseEntity {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;
    // ...
}
```

삭제는 실제 DELETE 대신 `deletedAt`에 시각을 기록합니다. 삭제자는 자동으로 채워지지 않으므로 `AuditorProvider`에서 값을 얻어 넘깁니다.

```java
place.delete(auditorProvider.current());
```

인증 정보가 없는 배치나 스케줄러에서는 `app.auditor.system-name` 값이 대신 들어갑니다.

### 4-5. 이벤트 발행

발행할 이벤트는 `domain/event/payload/`에 정의합니다. **`DomainEvent`를 구현하고, 라우팅 메서드 3개는 payload에 나가지 않습니다.** 인터페이스 선언에 `@JsonIgnore`가 붙어 있어 구현체가 그대로 상속받습니다.

```java
public record PolicyChangedEvent(
        UUID placeId,
        int policyVersion,
        List<String> changedFields,
        boolean hasConflict
) implements DomainEvent {

    @Override
    public String getTopic() {
        return "policy.changed";
    }

    @Override
    public String getAggregateType() {
        return "Policy";
    }

    @Override
    public String getAggregateId() {
        return placeId.toString();
    }
}
```

발행은 `OutboxEventRecorder` 한 줄입니다. 봉투 생성, 직렬화, outbox 행 저장, 커밋 후 발행 신호까지 여기서 처리합니다.

```java
@Transactional
public void applyMergedPolicy(UUID placeId, PetPolicy merged) {
    petPolicyRepository.save(merged);
    outboxEventRecorder.record(new PolicyChangedEvent(placeId, merged.getPolicyVersion(), ...));
}
```

**호출하는 메서드에 트랜잭션이 반드시 있어야 합니다.** 전파 속성이 `MANDATORY`이므로 트랜잭션 없이 부르면 즉시 예외가 납니다. 비즈니스 데이터와 outbox 행이 같은 트랜잭션으로 저장되어야 "둘 다 되거나 둘 다 안 된다"가 성립하기 때문입니다.

커밋 직후 `OutboxCommitListener`가 비동기로 발행을 시도하고, 놓친 건은 `OutboxRelay`가 주기적으로 회수합니다. Relay는 **한 인스턴스에서만** 켜야 합니다.

```yaml
app:
  outbox:
    relay:
      enabled: true
```

여러 인스턴스에서 동시에 돌면 같은 집합체의 선행 미발행 건을 각자 없다고 판단해 순서 보장이 깨집니다.

### 4-6. 이벤트 소비

**받는 서비스는 발행 서비스의 이벤트 클래스를 가져다 쓰지 않고, 자기 소비용 DTO를 따로 정의합니다.** 레포지터리가 나뉘어 있어 공유가 불가능하기도 하지만, 그보다 받는 쪽이 실제로 쓰는 필드만 선언할 수 있다는 점이 중요합니다.

DTO는 `infrastructure/message/kafka/consumer/dto/`에 둡니다.

```java
@JsonIgnoreProperties(ignoreUnknown = true)
public record PolicyChangedMessage(
        UUID placeId,
        int policyVersion
) {}
```

두 가지를 지킵니다.

- **`@JsonIgnoreProperties(ignoreUnknown = true)`는 필수입니다.** 없으면 발행 서비스가 필드를 하나 추가하는 순간 받는 쪽이 깨지고, 두 서비스의 배포 순서가 서로 묶입니다.
- **`DomainEvent`를 구현하지 않습니다.** `EventEnvelope<T>`에는 타입 제약이 없고 정적 팩터리 `of()`에서만 `DomainEvent`를 요구하므로, 받는 쪽은 순수 record면 충분합니다. 구현하면 받는 쪽에서 의미 없는 토픽·집합체 값을 채워야 합니다.

리스너는 `infrastructure/message/kafka/consumer/`에 둡니다. 봉투를 타입 그대로 받으면 `RecordMessageConverter`가 파라미터에 선언된 타입을 읽어 역직렬화합니다.

```java
@KafkaListener(topics = "policy.changed", groupId = "${spring.application.name}")
public void onPolicyChanged(EventEnvelope<PolicyChangedMessage> envelope) {
    inboxProcessor.processOnce(
            envelope.eventId(),
            envelope.eventType(),
            () -> searchIndexService.reindex(envelope.data())
    );
}
```

`topics`에 적는 문자열은 발행 쪽 `DomainEvent.getTopic()`이 반환하는 값과 **정확히 같아야 합니다.** 어긋나도 오류가 나지 않고 이벤트만 오지 않으므로 눈으로 확인합니다.

Kafka는 at-least-once이므로 같은 메시지를 두 번 받을 수 있습니다. `InboxProcessor`로 감싸면 처리 이력과 비즈니스 로직이 한 트랜잭션으로 묶여 중복 처리가 막힙니다.

**예외는 잡지 않고 그대로 던집니다.** 컨슈머 밖으로 나가야 프레임워크가 재시도와 DLQ 전송을 처리합니다. 1초부터 2배씩 늘려 3회 재시도하고, 그래도 실패하면 `{원본토픽}.dlq`로 보낸 뒤 오프셋을 넘깁니다.

---

## 5. 데이터베이스 마이그레이션

공통 모듈이 `db/migration/common/`에 스크립트를 들고 있으며, 각 서비스의 Flyway가 자기 DB에 실행합니다.

| 대역 | 위치 | 내용 |
|---|---|---|
| V1 ~ V19 | 공통 모듈 jar | `outbox`, `processed_event` |
| V20 ~ | 서비스 레포 | 서비스별 테이블 |

한 번 적용된 스크립트는 내용 해시로 검증되므로 수정할 수 없습니다. 공통 대역에 스크립트를 추가하면 이를 사용하는 모든 서비스에 적용되므로, 추가 시 팀에 알립니다.

---

## 6. 패키지 구조

```
com.pawtrail.common
│
├── config/                                     자동 설정 6개. 조건과 Bean 정의가 모두 여기에 모임
│   ├── CommonWebAutoConfiguration
│   ├── CommonSecurityAutoConfiguration
│   ├── CommonJpaAutoConfiguration
│   ├── CommonMessagingAutoConfiguration
│   ├── CommonKafkaAutoConfiguration
│   └── CommonAsyncAutoConfiguration
│
├── entity/
│   └── BaseEntity                              감사 컬럼 6개와 소프트 딜리트
│
├── audit/
│   └── AuditorProvider                         현재 작업 주체를 판정. 소프트 딜리트에서도 같은 값을 씀
│
├── enums/
│   └── Role                                    USER / ADMIN
│
├── exception/
│   ├── ErrorCode                               에러 코드가 가져야 할 계약
│   ├── CommonErrorCode                         전 서비스 공통 코드만
│   ├── CustomException                         의도적으로 던지는 유일한 예외
│   └── handler/GlobalExceptionHandler          예외를 응답 형식으로 변환
│
├── message/
│   ├── DomainEvent                             발행할 이벤트가 구현할 계약
│   ├── EventEnvelope                           모든 이벤트를 감싸는 봉투
│   ├── AuthContextHeaders                      인증 헤더 키의 단일 출처
│   ├── KafkaSecurityInterceptor                소비 시 헤더를 SecurityContext로 복원
│   │
│   ├── outbox/                                 저장은 됐는데 이벤트가 나가지 않는 상황을 막는 장치
│   │   ├── OutboxMessage
│   │   ├── OutboxRepository
│   │   ├── OutboxEventRecorder                 발행 입구. 서비스가 호출하는 곳
│   │   ├── OutboxPublisher                     실제 전송과 상태 기록의 단일 지점
│   │   ├── OutboxCommitListener                커밋 직후 즉시 발행
│   │   └── OutboxRelay                         놓친 건을 회수하는 안전망
│   │
│   └── inbox/                                  같은 이벤트를 두 번 처리하는 것을 막는 장치
│       ├── ProcessedEvent
│       ├── ProcessedEventRepository
│       └── InboxProcessor
│
├── response/
│   ├── CommonApiResponse
│   ├── PageResponse
│   └── TraceIdResponseAdvice                   응답 직전에 traceId 주입
│
└── security/
    ├── filter/HeaderAuthenticationFilter
    ├── handler/CustomSecurityExceptionHandler  401·403을 공통 응답 형식으로 반환
    ├── interceptor/RestClientAuthInterceptor   서비스 간 호출에 인증 헤더 전달
    ├── principal/CustomUserPrincipal
    └── annotation/CurrentUser
```

`resources/`에는 자동 설정 등록 파일과 공통 마이그레이션이 있습니다.

```
META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
db/migration/common/V1__outbox.sql
db/migration/common/V2__inbox.sql
```

---

## 7. 설정 프로퍼티

| 키 | 기본값 | 설명 |
|---|---|---|
| `app.auditor.system-name` | `SYSTEM` | 인증 정보가 없을 때 감사 컬럼에 들어갈 이름. 배치는 `ingest-batch` 등으로 지정 |
| `app.outbox.relay.enabled` | `false` | 회수 스케줄러 활성화. 서비스당 한 인스턴스에서만 |
| `app.outbox.relay.interval-ms` | `5000` | 회수 주기 |

---

## 8. 배포

라이브러리이므로 이미지가 아니라 jar를 GitHub Packages에 올립니다.

1. `build.gradle`의 `version`을 올립니다. **같은 버전은 덮어쓸 수 없으므로 publish 한 번이 번호 하나입니다.**
2. 자격증명을 환경변수로 주입합니다. `.env`는 Docker Compose가 읽는 파일이라 Gradle에는 보이지 않습니다.

```powershell
$env:GPR_USER = "<github-id>"
$env:GPR_TOKEN = "<write:packages 권한 토큰>"
./gradlew publish
```

3. 사용하는 서비스의 `gradle.properties`에서 `commonVersion`을 새 번호로 바꿉니다.

올리기 전에 jar 내용을 확인하는 편이 안전합니다. 자동 설정 등록 파일이 빠지면 아무 Bean도 올라오지 않는데 에러는 나지 않습니다.

```powershell
jar tf build\libs\common-<버전>.jar | Select-String "META-INF/spring|config/Common|db/migration"
```

---

## 9. 트러블슈팅

**`entityManagerFactory` Bean을 찾을 수 없다며 기동에 실패합니다**

DB를 쓰지 않는 서비스인데 JPA 자동 설정이 켜진 경우입니다. `build.gradle`에서 JPA 관련 의존성을 지울 때 `spring-boot-starter-data-jpa`뿐 아니라 `hibernate-spatial`과 QueryDSL 줄까지 함께 지웠는지 확인합니다. 이들이 남아 있으면 조건 판단이 어긋납니다.

**`package org.springframework.data.jpa.repository.config does not exist` 컴파일 오류가 납니다**

`build.gradle`에서 JPA를 제거하면서 애플리케이션 클래스의 `@EnableJpaRepositories`와 그 import를 함께 지우지 않은 경우입니다. 두 파일을 같이 고쳐야 합니다.

**공통 모듈의 Bean이 하나도 올라오지 않습니다**

jar 안에 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`가 들어갔는지 확인합니다. 그리고 `scanBasePackages`에 `com.pawtrail.common`을 넣지 않았는지도 함께 확인합니다.

**리스너에서 `MessageConversionException`이 납니다**

JSON 문자열을 봉투 타입으로 바꿔줄 `RecordMessageConverter`가 적용되지 않은 경우입니다. 서비스가 자기 `RecordMessageConverter`를 따로 정의하지 않았는지 확인합니다. Bean이 둘이 되면 어느 쪽도 적용되지 않아, 아예 없을 때와 같은 증상이 나타납니다.

**IntelliJ가 `Could not autowire`를 표시합니다**

오탐입니다. 이 모듈은 라이브러리라 `@SpringBootApplication`이 없어 IDE가 스프링 컨텍스트 모델을 만들지 못합니다. 빌드에는 영향이 없습니다.

---

## 10. 현재 상태

다음 항목은 아직 실물 환경에서 확인되지 않았습니다. Kafka 브로커와 PostgreSQL을 띄우는 시점에 함께 검증합니다.

- Outbox에서 Kafka를 거쳐 Inbox까지의 전체 흐름
- `InboxProcessor`의 트랜잭션 결합
- 재시도와 DLQ 전송
- Flyway 공통 마이그레이션 실행
- jsonb 컬럼 직렬화
- 봉투의 `occurredAt`이 발행·소비 양쪽에서 같은 형식으로 다루어지는지

`RestClientAuthInterceptor`는 클래스만 존재하며 아직 `RestClient.Builder`에 연결되어 있지 않습니다. 서비스 간 호출을 처음 구현하는 시점에 연결 방식을 정합니다.
