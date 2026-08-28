# common

paw-trail 조직의 서비스들이 공통으로 사용하는 라이브러리입니다. 실행되는 애플리케이션이 아니라 jar로 묶여 GitHub Packages에 배포되며, 각 서비스가 의존성으로 내려받아 사용합니다.

여기에 넣는 것은 **기술적 관심사이면서 거의 바뀌지 않는 것**입니다. 도메인 지식, 이벤트 payload DTO, 도메인 엔티티는 넣지 않습니다. 공통 모듈을 고치면 이를 사용하는 모든 서비스가 버전을 올려야 하므로, "여러 곳에서 쓰인다"만으로는 부족하고 "앞으로 거의 바뀌지 않는다"까지 만족해야 합니다.

넣지 않기로 한 것의 예는 다음과 같습니다.

- **토픽 이름 상수** — 토픽은 개발 도중 추가·변경·삭제될 수 있어 "거의 바뀌지 않는다"를 만족하지 못합니다
- **S3 업로드 유틸** — 사용하는 서비스가 일부뿐이고, 넣으면 AWS SDK 의존성이 전 서비스에 딸려갑니다
- **관리자용 컨트롤러** — 관리자가 할 일이 서비스마다 다릅니다

---

## 1. 제공하는 것

| 영역 | 제공하는 것 |
|---|---|
| 응답 | `CommonApiResponse`, `PageResponse`, traceId 자동 주입 |
| 예외 | `ErrorCode` 계약, `CommonErrorCode`, `CustomException`, 전역 예외 핸들러 |
| 인증 | 헤더 기반 인증 필터, `CustomUserPrincipal`, `@CurrentUser`, 기본 보안 체인, **관리자 경로 보호** |
| 엔티티 | `BaseEntity`(감사 컬럼 6개), 감사자 판정 |
| 이벤트 | 이벤트 봉투, Outbox 발행, Inbox 멱등 처리, 메시지 역직렬화, Consumer 예외 정책 |
| 스키마 | `outbox`·`processed_event` 테이블 마이그레이션 |
| 로깅 | Loki 전송용 logback appender 정의 |

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

DB를 사용하지 않는 서비스(verdict, congestion, route)는 `@EntityScan`과 `@EnableJpaRepositories`를 **애노테이션과 import까지 함께 제거합니다.** 남겨두면 JPA가 필수가 되어 컴파일 단계에서 실패합니다.

### 2-3. Flyway 위치

공통 마이그레이션은 jar 안에 들어 있으므로 서비스의 `application.yml`에 두 위치를 모두 지정합니다.

```yaml
spring:
  flyway:
    locations: classpath:db/migration/common,classpath:db/migration/service
```

**서비스의 스크립트는 `db/migration/` 바로 아래가 아니라 `db/migration/service/`에 둡니다.** 두 위치가 서로의 하위 경로이면 Flyway가 한쪽을 버립니다.

```
Discarding location 'classpath:db/migration/common'
  as it is a sub-location of 'classpath:db/migration'
```

이 경우에도 상위 경로를 훑으면서 하위 폴더를 함께 읽으므로 결과는 맞아 보이지만, 공통 대역과 서비스 대역이 경로 수준에서 분리되지 않은 상태입니다. 공통 모듈의 위치가 바뀌는 순간 깨지므로 두 경로를 형제로 둡니다.

### 2-4. 로깅

공통 모듈이 Loki 전송용 appender 정의를 `logback-loki-appender.xml`로 들고 있습니다. **정의만 들어 있으므로 서비스가 끌어다 쓰지 않으면 아무 일도 일어나지 않습니다.**

파일 이름을 `logback-spring.xml`로 두지 않은 이유는 그 이름이 클래스패스에서 하나만 읽히기 때문입니다. 서비스 레포의 `src/main/resources`가 jar보다 앞서므로 같은 이름으로 두면 공통 모듈 쪽이 무시됩니다.

서비스의 `src/main/resources/logback-spring.xml`을 다음과 같이 만듭니다.

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>

    <include resource="org/springframework/boot/logging/logback/defaults.xml"/>
    <include resource="org/springframework/boot/logging/logback/console-appender.xml"/>

    <springProperty name="appName" source="spring.application.name" defaultValue="unknown"/>
    <springProperty name="lokiUrl" source="app.logging.loki.url"
                    defaultValue="http://localhost:3100/loki/api/v1/push"/>

    <include resource="logback-loki-appender.xml"/>

    <root level="INFO">
        <appender-ref ref="CONSOLE"/>
        <springProfile name="!local">
            <appender-ref ref="LOKI"/>
        </springProfile>
    </root>

</configuration>
```

주의할 점이 셋 있습니다.

- **파일 이름이 반드시 `logback-spring.xml`이어야 합니다.** `logback.xml`로 두면 스프링 확장이 걸리지 않아 `<springProfile>`과 `<springProperty>`가 오류 없이 조용히 무시됩니다.
- **`<springProperty>`를 `<include>`보다 먼저 선언합니다.** 공통 모듈 쪽 XML이 그 값을 참조합니다.
- **`<appender-ref ref="LOKI"/>`를 빠뜨리면 로그가 전송되지 않습니다.** appender가 만들어져 있어도 root에 걸리지 않으면 아무 데도 흐르지 않으며, 오류는 나지 않습니다.

전송 여부는 프로파일로 가릅니다. `local`은 IntelliJ에서 실행하는 경우, `dev`는 컨테이너에서 실행하는 경우입니다. 서비스의 `application.yml`에 기본값을 둡니다.

```yaml
spring:
  profiles:
    default: local
```

`active`가 아니라 `default`인 점이 중요합니다. `default`는 아무도 지정하지 않았을 때만 적용되므로, 컨테이너에서 `SPRING_PROFILES_ACTIVE=dev`를 주면 그쪽이 우선합니다. IntelliJ에서는 별도 설정 없이 `local`로 동작해 Loki 전송이 꺼집니다.

appender 의존성인 `loki-logback-appender`는 공통 모듈이 `api`로 들고 있어 서비스까지 전파됩니다. 서비스가 따로 선언할 필요가 없습니다.

---

## 3. 자동 설정

`config/` 패키지의 6개 클래스가 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`에 등록되어 있습니다.

| 클래스 | 켜지는 조건 | 등록하는 Bean |
|---|---|---|
| `CommonWebAutoConfiguration` | 서블릿 웹 + spring-webmvc | `GlobalExceptionHandler`, `TraceIdResponseAdvice` |
| `CommonSecurityAutoConfiguration` | 서블릿 웹 + spring-security | `SecurityFilterChain`(**관리자 경로 보호 포함**), `CustomSecurityExceptionHandler` |
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

### 4-3. 인증과 권한

게이트웨이가 검증한 뒤 넣어주는 `X-User-Id`·`X-User-Role` 헤더를 필터가 읽어 `SecurityContext`를 채웁니다. 서비스는 토큰을 직접 다루지 않습니다.

```java
@GetMapping("/api/v1/pets")
public CommonApiResponse<List<PetResponse>> getMyPets(@CurrentUser CustomUserPrincipal principal) {
    return CommonApiResponse.success(petService.findByAccount(principal.accountId()));
}
```

`CustomUserPrincipal`은 `accountId(UUID)`와 `role(Role)` 둘만 가집니다.

#### 기본 보안 체인의 경로 규칙

| 경로 | 규칙 |
|---|---|
| `/internal/**`, `/actuator/**` | 인증 없이 허용 |
| **`/api/v1/admin/**`** | **`ADMIN` 역할만 허용** |
| 그 외 전부 | 인증 필수 |

관리자 API를 공통 모듈에서 막는 이유는 **관리자 기능이 여러 서비스에 흩어져 있기 때문입니다.** 제보 처리는 report, 조건 정정은 policy, 폐업 처리는 place, 재색인은 search에 있습니다. 각 서비스가 알아서 막게 하면 **한 곳만 빠뜨려도 그 서비스가 그대로 열립니다.**

`hasRole("ADMIN")`이 동작하는 것은 `HeaderAuthenticationFilter`가 권한을 `"ROLE_" + role` 형태로 만들기 때문입니다. 스프링 시큐리티의 `hasRole`은 `ROLE_` 접두사를 자동으로 붙여 찾습니다. 접두사 규칙을 바꾸면 관리자 경로가 **403만 반환하고 원인이 드러나지 않으므로** 건드리지 않습니다.

`/internal/**`을 열어두는 것은 게이트웨이가 이 경로를 라우팅하지 않아 외부에서 도달할 수 없다는 전제 위에 있습니다. 다만 **네트워크 격리는 외부 침입을 막을 뿐, 정상 사용자가 남의 데이터를 조회하는 것은 막지 못합니다.** 다른 사용자의 자원을 다루는 `/internal` API는 `X-User-Id`와 소유자가 일치하는지 각 서비스가 직접 확인해야 합니다.

로그인 경로처럼 열어야 하는 서비스는 자체 `SecurityFilterChain`을 정의하면 되고, 그러면 공통 모듈 쪽이 물러납니다. **다만 그 경우 관리자 경로 보호도 함께 사라지므로 직접 넣어야 합니다.**

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

`topics`에 적는 문자열은 발행 쪽 `DomainEvent.getTopic()`이 반환하는 값과 **정확히 같아야 합니다.** 토픽 이름은 공통 모듈에 상수로 두지 않으므로 같은 문자열이 두 레포에 각각 존재하며, **어긋나도 오류가 나지 않고 이벤트만 오지 않습니다.** 전체 토픽 목록은 service-template README의 이벤트 표를 참조하고, 실물 확인은 Kafka UI에서 토픽에 메시지가 쌓였는지로 합니다.

Kafka는 at-least-once이므로 같은 메시지를 두 번 받을 수 있습니다. `InboxProcessor`로 감싸면 처리 이력과 비즈니스 로직이 한 트랜잭션으로 묶여 중복 처리가 막힙니다.

**예외는 잡지 않고 그대로 던집니다.** 컨슈머 밖으로 나가야 프레임워크가 재시도와 DLQ 전송을 처리합니다. 1초부터 2배씩 늘려 3회 재시도하고, 그래도 실패하면 `{원본토픽}.dlq`로 보낸 뒤 오프셋을 넘깁니다.

---

## 5. 데이터베이스 마이그레이션

공통 모듈이 `db/migration/common/`에 스크립트를 들고 있으며, 각 서비스의 Flyway가 자기 DB에 실행합니다.

| 대역 | 위치 | 내용 |
|---|---|---|
| V1 ~ V19 | 공통 모듈 jar | `outbox`, `processed_event` |
| V20 ~ | 서비스 레포 `db/migration/service/` | 서비스별 테이블 |

한 번 적용된 스크립트는 내용 해시로 검증되므로 수정할 수 없습니다. 공통 대역에 스크립트를 추가하면 이를 사용하는 모든 서비스에 적용되므로, 추가 시 팀에 알립니다.

---

## 6. 패키지 구조

```
com.pawtrail.common
│
├── config/                                         자동 설정 6개. 조건과 Bean 정의가 모두 여기 모입니다.
│   ├── CommonWebAutoConfiguration.java (class)     자동 설정 클래스는 컴포넌트 스캔에 걸리면 안 되는
│   ├── CommonSecurityAutoConfiguration.java        특수한 부류라 한 폴더에 격리해 둡니다
│   ├── CommonJpaAutoConfiguration.java
│   ├── CommonMessagingAutoConfiguration.java
│   ├── CommonKafkaAutoConfiguration.java
│   └── CommonAsyncAutoConfiguration.java
│
├── entity/
│   └── BaseEntity.java (abstract class)            모든 테이블이 상속하는 공통 컬럼 묶음입니다.
│                                                   createdAt·createdBy, updatedAt·updatedBy,
│                                                   deletedAt·deletedBy 를 가집니다. 소프트 딜리트를 쓰므로
│                                                   실제 DELETE 를 하지 않고 deletedAt 에 시각을 기록합니다.
│                                                   NULL 이면 살아있는 행입니다.
│                                                   시각은 전부 LocalDateTime 이며 DB 컬럼은 timestamp 입니다
│
├── audit/
│   └── AuditorProvider.java (class)                "지금 이 작업을 하는 주체가 누구인가"를 한 곳에서
│                                                   알려줍니다. 삭제 시 deletedBy 에 넣을 값을 여기서 얻습니다.
│                                                   생성·수정은 JPA 가 자동으로 채우는데 삭제만 수동이므로,
│                                                   값의 출처가 갈리지 않도록 두는 장치입니다.
│                                                   인증이 없으면 app.auditor.system-name 값을 씁니다
│
├── enums/
│   └── Role.java (enum)                            USER / ADMIN. 게이트웨이가 X-User-Role 로 주입하는
│                                                   값이며, 필터가 "ROLE_" 접두사를 붙여 권한으로 만듭니다
│
├── exception/
│   ├── ErrorCode.java (interface)                  에러 코드가 가져야 할 모양만 정의합니다.
│   │                                               getHttpStatus, getCode, getMessage 세 개이며
│   │                                               각 서비스가 자기 enum 으로 구현합니다.
│   │                                               getCode() 는 반드시 name() 을 그대로 반환합니다.
│   │                                               상수 이름이 곧 응답 code 이자 API 계약인데
│   │                                               규칙을 어겨도 컴파일러가 잡지 못합니다
│   ├── CommonErrorCode.java (enum)                 모든 서비스에서 같은 뜻으로 쓰이는 에러만 담습니다.
│   │                                               VALIDATION_FAILED(400)
│   │                                               AUTHENTICATION_FAILED(401)
│   │                                               ACCESS_DENIED(403)
│   │                                               INTERNAL_ERROR(500)
│   │                                               EXTERNAL_API_ERROR(502)
│   ├── CustomException.java (class)                의도적으로 던지는 모든 예외입니다. ErrorCode 를 하나
│   │                                               물고 있으며, 핸들러가 거기서 HTTP 상태와 메시지를 꺼냅니다.
│   │                                               예외 클래스를 상태별로 나누지 않는 이유는, 상태값이 이미
│   │                                               ErrorCode 에 있어 클래스가 두 번째 진실의 원천이 되면
│   │                                               둘이 어긋나도 아무도 알아채지 못하기 때문입니다
│   └── handler/
│       └── GlobalExceptionHandler.java (class)
│                                                   모든 예외를 잡아 응답 형식으로 바꾸는 곳입니다.
│                                                   핸들러는 4개입니다.
│                                                   (1) CustomException → ErrorCode 의 상태로 응답
│                                                   (2) MethodArgumentNotValidException(@Valid 실패)
│                                                       → 400 과 함께 필드별 오류 배열 반환
│                                                   (3) MethodArgumentTypeMismatchException
│                                                       (/places/abc 처럼 타입 불일치) → 400
│                                                   (4) Exception → 500
│                                                   401·403 은 여기로 오지 않습니다. 시큐리티 필터가
│                                                   DispatcherServlet 앞에 있어 아래 핸들러가 처리합니다
│
├── message/                                        이벤트 발행·수신의 뼈대
│   ├── DomainEvent.java (interface)                발행할 이벤트가 구현하는 계약입니다.
│   │                                               getTopic, getAggregateType, getAggregateId 세 개이며
│   │                                               셋 다 @JsonIgnore 라 payload 에는 나가지 않습니다.
│   │                                               이벤트가 자기 라우팅 정보를 들고 다니므로 발행할 때
│   │                                               토픽을 따로 넘기지 않습니다
│   ├── EventEnvelope.java (record)                 모든 이벤트를 감싸는 봉투입니다. eventId(중복 판단 키),
│   │                                               eventType, occurredAt, aggregateType, aggregateId,
│   │                                               data 로 구성됩니다.
│   │                                               봉투는 공통에 두지만 data 안쪽 DTO 는 각 서비스가
│   │                                               따로 정의합니다. 결합을 피하기 위함입니다.
│   │                                               제네릭에 타입 제약이 없어 받는 쪽 DTO 는
│   │                                               DomainEvent 를 구현하지 않아도 됩니다
│   ├── AuthContextHeaders.java (final class)       X-User-Id·X-User-Role 헤더 키의 단일 출처입니다.
│   │                                               HTTP 필터, 서비스 간 호출 인터셉터, 카프카 인터셉터가
│   │                                               같은 문자열을 각자 들고 있으면 어긋나도 알 수 없습니다
│   ├── KafkaSecurityInterceptor.java (class)       소비 시 카프카 헤더를 SecurityContext 로 복원합니다.
│   │                                               컨슈머는 HTTP 요청 밖 스레드에서 실행되어 컨텍스트가
│   │                                               비어 있고, 복원하지 않으면 컨슈머가 만든 행의
│   │                                               createdBy 가 전부 SYSTEM 으로 남습니다.
│   │                                               traceparent 는 다루지 않습니다. 스프링 카프카
│   │                                               Observation 이 처리하며 직접 넣으면 헤더가 중복됩니다
│   │
│   ├── outbox/                                     "DB에는 저장됐는데 이벤트는 나가지 않았다"를 막는 장치
│   │   ├── OutboxMessage.java (entity)             발행 대기 중인 이벤트 한 건입니다. 비즈니스 데이터와
│   │   │                                           같은 트랜잭션으로 저장되므로 둘 다 되거나 둘 다 안 됩니다
│   │   ├── OutboxRepository.java (interface)
│   │   │                                           미발행 건을 조회합니다. 같은 aggregateId 에 대해
│   │   │                                           앞선 미발행 건이 있는지도 확인해 순서를 보장합니다.
│   │   │                                           재시도 10회를 넘긴 건은 조회에서 제외합니다.
│   │   │                                           영구 실패 한 건이 뒤 메시지를 전부 막기 때문입니다
│   │   ├── OutboxEventRecorder.java (class)        ★서비스가 호출하는 발행 입구입니다.
│   │   │                                           record(이벤트) 한 줄로 봉투 생성·직렬화·행 저장·
│   │   │                                           커밋 후 발행 신호까지 처리합니다.
│   │   │                                           전파 속성이 MANDATORY 라 트랜잭션 없이 부르면
│   │   │                                           즉시 예외가 납니다. 비즈니스 데이터와 같은
│   │   │                                           트랜잭션이어야 Outbox 가 성립하기 때문입니다
│   │   ├── OutboxPublisher.java (class)            실제 카프카 전송과 상태 기록의 단일 지점입니다.
│   │   │                                           건당 독립 트랜잭션이라 한 건이 실패해도 앞서 성공한
│   │   │                                           건의 상태 갱신은 유지됩니다
│   │   ├── OutboxCommitListener.java (class)
│   │   │                                           커밋 직후 비동기로 발행을 시작합니다.
│   │   │                                           정상 경로는 여기서 처리되므로 지연이 거의 없습니다
│   │   └── OutboxRelay.java (class)                놓친 건을 회수하는 안전망 스케줄러입니다(5초 주기).
│   │                                               app.outbox.relay.enabled 로 한 인스턴스에서만
│   │                                               실행합니다. 여러 인스턴스가 동시에 돌면 서로
│   │                                               "앞에 미발행 건이 없다"고 판단해 순서 보장이 깨집니다
│   │
│   └── inbox/                                      "같은 이벤트를 두 번 처리했다"를 막는 장치
│       ├── ProcessedEvent.java (entity)            처리한 eventId 기록입니다. PK 충돌 자체가 멱등 장치라
│       │                                           별도 조회가 필요 없습니다.
│       │                                           ID 가 발행자에게서 온 값이라 Persistable 을 구현해
│       │                                           항상 persist 가 나가게 합니다. 그러지 않으면 merge 가
│       │                                           호출되어 PK 충돌 없이 UPDATE 로 흘러갑니다
│       ├── ProcessedEventRepository.java (interface)
│       │                                           existsById 와 save 만 사용합니다
│       └── InboxProcessor.java (class)             processOnce(eventId, topic, 로직) 형태로 사용합니다.
│                                                   기록과 비즈니스 로직을 한 트랜잭션으로 묶어
│                                                   "처리했다고 기록했는데 실제로는 실패"와
│                                                   "처리는 했는데 기록이 실패"를 둘 다 막습니다
│
├── response/
│   ├── CommonApiResponse.java (class)              모든 API 응답의 겉껍데기입니다.
│   │                                               { code, message, data, traceId }
│   │                                               성공은 code 가 SUCCESS 입니다
│   ├── PageResponse.java (record)                  목록 응답에서 data 안에 들어가는 형태입니다.
│   │                                               { content: [...], page: { number, size,
│   │                                                 totalElements, totalPages } }
│   │                                               from(Page, 변환함수) 로 만들며 엔티티를 그대로
│   │                                               노출하지 않게 합니다
│   └── TraceIdResponseAdvice.java (class)          응답 직전에 traceId 를 채웁니다. 컨트롤러가 신경 쓸
│                                                   필요가 없고, 성공 응답에도 실립니다. 문의가 들어왔을 때
│                                                   해당 요청을 분산 추적에서 바로 찾기 위함입니다
│
└── security/
    ├── filter/HeaderAuthenticationFilter.java (class)
    │                                               게이트웨이가 넣어준 X-User-Id·X-User-Role 헤더를 읽어
    │                                               SecurityContext 를 채웁니다. 뒤쪽 서비스는 JWT 를 직접
    │                                               다루지 않습니다. 토큰 검증은 게이트웨이에서 끝났습니다.
    │                                               권한은 "ROLE_" + role 형태로 만듭니다. 이 접두사가
    │                                               있어야 보안 체인의 hasRole("ADMIN") 이 동작합니다.
    │                                               Bean 이 아니라 SecurityConfig 에서 직접 생성합니다.
    │                                               Bean 으로 두면 서블릿 전역 필터에도 등록돼 두 번 돕니다
    ├── handler/CustomSecurityExceptionHandler.java (class)
    │                                               401·403 을 공통 응답 형식으로 반환합니다.
    │                                               시큐리티 필터는 DispatcherServlet 앞이라
    │                                               GlobalExceptionHandler 가 잡지 못합니다.
    │                                               이것이 없으면 인증 실패만 응답 형태가 달라집니다
    ├── interceptor/RestClientAuthInterceptor.java (class)
    │                                               서비스가 다른 서비스를 호출할 때 X-User-Id 를 헤더에
    │                                               실어줍니다. 이것이 없으면 호출받은 쪽이 요청자를 알 수
    │                                               없어 감사 컬럼이 시스템 계정으로 기록됩니다.
    │                                               traceparent 는 넣지 않습니다. 분산 추적 라이브러리가
    │                                               자동 처리하며, 직접 넣으면 트레이스가 갈라집니다.
    │                                               ※ 아직 RestClient.Builder 에 연결되어 있지 않습니다.
    │                                                 서비스 간 호출을 처음 구현할 때 연결 방식을 정합니다
    ├── principal/CustomUserPrincipal.java (record)
    │                                               SecurityContext 에 담기는 사용자 정보입니다.
    │                                               accountId(UUID) 와 role(Role) 둘만 가집니다
    └── annotation/CurrentUser.java (annotation)
                                                    컨트롤러에서 사용자를 주입받는 애노테이션입니다

src/main/resources/
├── META-INF/spring/
│   └── org.springframework.boot.autoconfigure.AutoConfiguration.imports
│                                                   위 config 6개를 자동 설정으로 등록합니다.
│                                                   이 파일이 jar 에 들어가지 않으면 아무 Bean 도
│                                                   올라오지 않는데 오류는 나지 않습니다
└── db/migration/common/
    ├── V1__outbox.sql                              outbox 테이블입니다. 공통이므로 V1~V19 대역을 씁니다
    └── V2__inbox.sql                               processed_event 테이블입니다
```

---

## 7. 설정 프로퍼티

| 키 | 기본값 | 설명 |
|---|---|---|
| `app.auditor.system-name` | `SYSTEM` | 인증 정보가 없을 때 감사 컬럼에 들어갈 이름. 배치는 `ingest-batch` 등으로 지정 |
| `app.outbox.relay.enabled` | `false` | 회수 스케줄러 활성화. 서비스당 한 인스턴스에서만 |
| `app.outbox.relay.interval-ms` | `5000` | 회수 주기 |
| `app.logging.loki.url` | `http://localhost:3100/loki/api/v1/push` | Loki 전송 주소. `logback-spring.xml`이 읽습니다 |

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

**관리자 API가 계속 403을 반환합니다**

`X-User-Role` 헤더가 `ADMIN`으로 들어오는지, 그리고 `HeaderAuthenticationFilter`가 권한을 `"ROLE_" + role` 형태로 만드는지 확인합니다. 접두사가 빠지면 `hasRole("ADMIN")`이 절대 통과하지 못하는데, **오류 메시지에는 원인이 드러나지 않습니다.**

**리스너에서 `MessageConversionException`이 납니다**

JSON 문자열을 봉투 타입으로 바꿔줄 `RecordMessageConverter`가 적용되지 않은 경우입니다. 서비스가 자기 `RecordMessageConverter`를 따로 정의하지 않았는지 확인합니다. Bean이 둘이 되면 어느 쪽도 적용되지 않아, 아예 없을 때와 같은 증상이 나타납니다.

**이벤트를 발행했는데 받는 쪽이 반응하지 않습니다**

토픽 문자열이 어긋났을 가능성이 큽니다. 발행 쪽 `getTopic()`과 받는 쪽 `@KafkaListener(topics = ...)`를 대조하고, Kafka UI에서 토픽에 메시지가 실제로 쌓였는지 확인합니다. 쌓여 있다면 받는 쪽 문제, 비어 있다면 발행 쪽 문제입니다.

**Loki에 로그가 하나도 들어가지 않습니다**

먼저 Loki가 무엇이든 받았는지 확인합니다.

```powershell
curl.exe "http://localhost:3100/loki/api/v1/labels"
```

응답에 `data` 필드가 아예 없다면 전송된 로그가 하나도 없는 상태입니다. 다음 순서로 확인합니다.

1. 서비스에 `src/main/resources/logback-spring.xml`이 있는지. 공통 모듈의 `logback-loki-appender.xml`은 정의만 들고 있어 단독으로는 동작하지 않습니다.
2. 그 파일의 root에 `<appender-ref ref="LOKI"/>`가 걸려 있는지.
3. 실행 프로파일이 `local`이 아닌지. 기동 로그 첫머리의 `The following 1 profile is active` 줄로 확인합니다.

appender 생성 자체가 실패했다면 스프링 배너 앞뒤에 `ERROR in ch.qos.logback...` 한 줄이 출력됩니다. 스택트레이스가 아니라 짧은 줄이라 놓치기 쉽습니다. 이 경우에도 애플리케이션은 정상 기동하고 로그만 전송되지 않습니다.

**IntelliJ가 `Could not autowire`를 표시합니다**

오탐입니다. 이 모듈은 라이브러리라 `@SpringBootApplication`이 없어 IDE가 스프링 컨텍스트 모델을 만들지 못합니다. 빌드에는 영향이 없습니다.

---

## 10. 현재 상태

Kafka 브로커와 PostgreSQL을 띄운 환경에서 다음 항목을 확인했습니다.

- Outbox에서 Kafka를 거쳐 Inbox까지의 전체 흐름
- `InboxProcessor`의 멱등 처리. 같은 이벤트를 다시 받으면 비즈니스 로직을 실행하지 않습니다
- `OutboxEventRecorder`의 `MANDATORY` 전파. 트랜잭션 없이 호출하면 예외가 발생합니다
- 기록 직후 예외를 던졌을 때 outbox 행이 함께 롤백되는지
- 재시도와 DLQ 전송. 1초, 2초, 4초 간격으로 시도한 뒤 `{토픽}.dlq`로 이동합니다
- Flyway 공통 마이그레이션 실행
- jsonb 컬럼 직렬화. 봉투의 라우팅 필드가 payload로 새지 않는 것도 함께 확인했습니다
- 봉투의 `occurredAt`이 발행·소비 양쪽에서 같은 형식으로 다루어지는지
- 기본 보안 체인이 Boot 기본 설정보다 우선 적용되는지
- traceId가 Kafka를 건너 소비 쪽까지 이어지는지

재시도 횟수는 `maxAttempts`가 3이지만 실제 시도는 최초 1회를 포함해 4회입니다.

다음 항목은 아직 확인하지 않았습니다.

- `KafkaSecurityInterceptor`가 리스너 컨테이너에 적용되는지. 인증 헤더가 실린 이벤트가 필요하므로 auth 서비스를 만든 뒤 확인합니다
- 관리자 경로가 `ADMIN` 역할로만 통과하는지

`RestClientAuthInterceptor`는 클래스만 존재하며 아직 `RestClient.Builder`에 연결되어 있지 않습니다. 서비스 간 호출을 처음 구현하는 시점에 연결 방식을 정합니다. 연결이 되었는지는 서비스 A가 인증된 요청을 처리하면서 B를 호출했을 때 **B가 만든 행의 `created_by`가 `SYSTEM`이 아니라 실제 계정 식별자인지**로 확인합니다. 연결되지 않아도 오류는 발생하지 않습니다.
