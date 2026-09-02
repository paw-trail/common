# common

**함께하개의 공통 모듈입니다.** 도메인 서비스 14개가 전부 쓰는 것만 담습니다.

```
                    [ GitHub Packages ]
                             │
                             │  com.pawtrail:common:0.0.9  (jar)
                             ▼
도메인 서비스 14개  ──▶  공통 모듈   자동 설정 6개가 조건에 맞으면 켜짐
        │                    │
        │                    ├──▶  응답 형식 · 예외 처리
        │                    ├──▶  인증 헤더 → SecurityContext
        │                    ├──▶  BaseEntity · 감사 컬럼
        │                    ├──▶  Outbox · Inbox
        │                    └──▶  Flyway V1 · V2
        │
        └──▶  각자 도메인 코드만 씀
```

**서비스는 의존성 한 줄만 추가하면 됩니다.** 나머지는 자동 설정이 알아서 켭니다.

<br><br>

---

## 0. 이 모듈이 하는 일

**공통 모듈이 없으면 이렇게 됩니다.**

| | 있으면 | 없으면 |
|---|---|---|
| 응답 형식 | 한 곳에서 정의 | **서비스 14개가 각자 만듦 → 형태가 갈림** |
| 예외 처리 | 자동으로 붙음 | 서비스마다 `@RestControllerAdvice` 를 복사 |
| 감사 컬럼 | `BaseEntity` 상속 | 테이블마다 6컬럼을 직접 |
| Outbox | `record()` 한 줄 | **5단계를 손으로** — 하나만 빠뜨려도 이벤트가 안 나감 |
| 인증 | 헤더를 읽어 자동으로 채움 | 필터를 14번 복사 |

---

**숫자로 보면 이렇습니다.**

| | 값 | 어디에 |
|---|---|---|
| 자바 파일 | **34개** | [3장](#3-무엇이-들어-있나) |
| 자동 설정 | 6개 | [2장](#2-자동-설정-6개) |
| Flyway 스크립트 | 2개 (`V1`·`V2`) | [3-5](#3-5-messageoutbox--이벤트를-안전하게-보내기) |
| 공통 에러 코드 | 6개 | [3-2](#3-2-exception--에러-코드-규약) |
| 현재 버전 | **0.0.9** | [5장](#5-버전을-올리고-배포하기) |
| 소비하는 서비스 | 도메인 14개 | 플랫폼 3개는 **안 씀** |

---

**넣는 것과 안 넣는 것의 기준입니다.**

```
넣음      도메인 서비스가 전부 쓰는 것
          응답 형식 · 예외 · 인증 · 감사 · Outbox · Inbox

안 넣음   특정 서비스만 쓰는 것
          PLACE_NOT_FOUND 같은 도메인 에러 코드
          토픽 이름 상수
          ROUTE_NOT_FOUND (게이트웨이만 씀)
```

> **토픽 이름을 여기 두지 않는 이유** — 토픽은 개발 도중 추가·변경되는데
> 공통 모듈에 있으면 **그때마다 재배포와 전 서비스 버전업**이 필요합니다.
> **이 모듈의 기준은 "거의 바뀌지 않는 것"** 입니다.

<br><br>

---

### 먼저 알아 두면 좋은 것 4가지

**아래를 모르면 이 문서가 안 읽힙니다.** 한 문단씩만 보고 갑니다.

---

**① 라이브러리란 — 이 저장소는 실행되지 않습니다**

```
서비스 저장소 (auth · place ...)        이 저장소 (common)

  빌드  ──▶  실행 가능한 jar              빌드  ──▶  끼워 넣는 jar
              java -jar 로 뜸                        혼자서는 아무것도 안 함
              도커 이미지가 됨                        *다른 서비스의 build.gradle 에 한 줄로 들어감
```

**GitHub Packages 에 올려 두고 각 서비스가 내려받습니다.** `commonVersion=0.0.9` 가 그것입니다.

---

**② 자동 설정이란 — 의존성만 넣으면 알아서 켜집니다**

```
서비스가 build.gradle 에 common 을 추가
        │
        ▼
스프링 부트가 기동하며 jar 안의 목록을 읽음        META-INF/spring/...AutoConfiguration.imports
        │
        ▼
목록의 설정 클래스 6개를 하나씩 봄
        "이 서비스에 JPA 가 있나?"  →  있으면 JPA 관련 Bean 을 올림
        "Kafka 가 있나?"           →  있으면 Kafka 관련 Bean 을 올림
        │
        ▼
서비스는 아무것도 등록하지 않았는데 응답 형식 · 예외 처리 · 인증이 붙어 있음
```

**"클래스패스에 있나" 가 판단 기준입니다.** 클래스패스란 **그 서비스가 빌드에 넣은
라이브러리 전부**를 뜻합니다. `spring-data-jpa` 를 넣었으면 클래스패스에 있는 것입니다.

---

**③ 이벤트란 — 서비스끼리 직접 부르지 않는 방법**

```
직접 호출                              이벤트

auth  ──▶  user  "프로필 만들어"         auth  ──▶  Kafka  "계정이 생겼음"
              │                                         │
              └── user 가 죽어 있으면                    └──▶  user 가 나중에 읽어 감
                  가입이 실패함                                auth 는 user 를 몰라도 됨
```

**auth 가 `account.created` 를 발행하면 user 가 그것을 받아 프로필을 만듭니다.**
auth 는 user 가 떠 있는지, 몇 개인지, 어디 있는지 몰라도 됩니다.

---

**④ Outbox 가 왜 필요한가 — "저장은 됐는데 이벤트가 안 나갔다"**

```
⛔ 그냥 하면

  ① DB 에 계정 저장       성공
  ② Kafka 에 이벤트 발행   실패 (Kafka 가 잠깐 죽음)
        │
        └── 계정은 있는데 프로필이 영영 안 생김. 아무도 모름


✅ Outbox 를 쓰면

  ① DB 에 계정 저장       ─┐
  ② DB 에 이벤트도 저장    ─┴── 같은 트랜잭션 → 둘 다 되거나 둘 다 안 됨
        │
        └──▶  ③ 별도 작업이 outbox 표를 읽어 Kafka 로 보냄
                    실패하면 5초마다 다시 시도
```

**Inbox 는 반대쪽입니다.** 같은 이벤트가 두 번 오면(재시도·리밸런싱) **한 번만 처리**하게
막습니다.

<br><br>

---

### 이 문서를 읽는 순서

| 지금 하려는 일 | 볼 곳 |
|---|---|
| 새 서비스에 붙이려 한다 | [1장](#1-서비스에-붙이기) |
| 자동 설정이 왜 안 켜지는지 모르겠다 | [2장](#2-자동-설정-6개) → [6-1](#6-1-자동-설정이-안-켜질-때) |
| 무엇이 들어 있는지 | [3장](#3-무엇이-들어-있나) |
| 코드에서 어떻게 쓰는지 | [4장](#4-쓰는-법) |
| 공통 모듈을 고쳐 배포해야 한다 | [5장](#5-버전을-올리고-배포하기) |
| 뭔가 안 된다 | [6장](#6-막히기-쉬운-자리) |
| "왜 이렇게 만들었지" | [7장](#7-왜-이렇게-만들었나) |
| 지금 상태가 궁금하다 | [8장](#8-현재-상태) |
| 모르는 말이 나온다 | [9장](#9-용어) |

> **이 모듈을 고치는 일은 드뭅니다.** 대부분은 [1장](#1-서비스에-붙이기)·[4장](#4-쓰는-법)
> 만 보면 됩니다. [5장](#5-버전을-올리고-배포하기) 은 실제로 고칠 때만 봅니다.

---

**라이브러리라 다른 레포와 다릅니다.**

```
1  실행되지 않음           bootJar 를 끄고 jar 를 켬 (다른 레포는 반대)
                          @SpringBootApplication 이 없음

2  GitHub Packages 로 배포  ghcr 이미지가 아니라 Maven 패키지

3  버전이 영구적            같은 버전을 덮어쓸 수 없음
                          publish 한 번이 번호 하나를 소모함

4  IntelliJ 가 오탐을 냄     Could not autowire — 컨텍스트 모델을 못 만들어서
                          빌드는 통과함
```

<br><br>

---

## 1. 서비스에 붙이기

**`service-template` 을 복제했다면 ①②④는 이미 되어 있습니다.**
③만 확인하면 됩니다.

```
① build.gradle 에 저장소와 의존성
        │
        │   repositories { maven { url = "https://maven.pkg.github.com/paw-trail/common" } }
        │   implementation "com.pawtrail:common:${commonVersion}"
        ▼
② 환경변수 GPR_USER · GPR_TOKEN            없으면 401 로 빌드 실패
        │
        ▼
③ 앱 클래스에 @EntityScan · @EnableJpaRepositories
        │      두 곳 다 com.pawtrail.common 을 포함
        │      ⛔ scanBasePackages 에는 넣지 말 것
        ▼
④ config 에 spring.flyway.locations
        │      classpath:db/migration/common,classpath:db/migration/service
        ▼
⑤ 기동                                     자동 설정 6개가 조건을 보고 켜짐
```

<br><br>

---

### 1-1. build.gradle

```groovy
repositories {
    mavenCentral()

    // 공통 모듈은 GitHub Packages 에 있음
    // 공개 저장소라도 내려받을 때 인증을 요구함
    maven {
        name = "GitHubPackages"
        url = uri("https://maven.pkg.github.com/paw-trail/common")
        credentials {
            username = System.getenv("GPR_USER")
            password = System.getenv("GPR_TOKEN")
        }
    }
}

dependencies {
    implementation "com.pawtrail:common:${commonVersion}"

    // 공통 모듈의 PageResponse 가 Page 를 씀
    // starter-data-redis 가 전이로 끌어오지만 페이징이 캐시 의존성에 매달리므로 직접 선언
    // DB 를 쓰지 않는 서비스도 목록 응답을 반환하므로 이 줄은 남길 것
    implementation 'org.springframework.data:spring-data-commons'
}
```

버전은 `gradle.properties` 에 한 줄로 둡니다.

```properties
commonVersion=0.0.9
```

> **최신 버전은 조직의 Packages 페이지에서 확인합니다.** 이 문서의 숫자가
> 낡았을 수 있습니다.

---

**환경변수 둘이 필요합니다.**

| 이름 | 값 | 권한 |
|---|---|---|
| `GPR_USER` | GitHub 사용자명 | — |
| `GPR_TOKEN` | Personal Access Token (classic) | 받기만 하면 `read:packages` |

**OS 환경변수에 둡니다.** Gradle 빌드는 IntelliJ 실행 구성을 읽지 않습니다.

```
Could not GET 'https://maven.pkg.github.com/paw-trail/common/...'
Received status code 401 from server: Unauthorized
```

**이 오류가 나면 토큰 문제입니다.** 메시지에 그렇게 안 적혀 있습니다.

<br><br>

---

### 1-2. 앱 클래스 — 여기만 손으로 확인합니다

```java
@SpringBootApplication
@EntityScan(basePackages = {"com.pawtrail.place", "com.pawtrail.common"})
@EnableJpaRepositories(basePackages = {"com.pawtrail.place", "com.pawtrail.common"})
public class PlaceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PlaceApplication.class, args);
    }
}
```

| 애노테이션 | 왜 필요 |
|---|---|
| `@EntityScan` | `OutboxMessage` · `ProcessedEvent` 가 이 모듈에 있음 |
| `@EnableJpaRepositories` | `OutboxRepository` · `ProcessedEventRepository` |

**둘 다 자동 설정으로는 안 잡힙니다.** 엔티티와 리포지터리는 스캔 대상이라
앱 클래스가 알려 줘야 합니다.

---

**⛔ `scanBasePackages` 에는 넣지 않습니다.**

```java
// 이렇게 하면 안 됩니다
@SpringBootApplication(scanBasePackages = {"com.pawtrail.place", "com.pawtrail.common"})
```

```
자동 설정 클래스가 컴포넌트 스캔에 걸림
        │
        ├──▶  처리 순서가 깨져 조건 평가가 뒤집힘
        └──▶  같은 @Configuration 이 두 번 등록될 수 있음
                스프링 부트가 명시적으로 금하는 형태
```

**빈은 전부 자동 설정의 `@Bean` 으로 등록됩니다.** 스캔이 필요 없습니다.

---

**DB 를 쓰지 않는 서비스는 둘 다 지웁니다.**

`verdict` · `congestion` · `route` 가 여기 해당합니다.
`import` 도 함께 지워야 컴파일이 통과합니다. `service-template` README 1-5 참고.

<br><br>

---

### 1-3. Flyway 위치

```yaml
# config 저장소 1계층 — application.yml
spring:
  flyway:
    locations: classpath:db/migration/common,classpath:db/migration/service
```

**두 곳을 다 적어야 합니다.** 하나만 적으면 그쪽만 실행됩니다.

```
db/migration/common/     V1 ~ V19   공통 모듈 대역 (jar 안에 있음)
├── V1__outbox.sql
└── V2__inbox.sql

db/migration/service/    V20 ~      각 서비스
└── V20__place.sql
```

> **대역을 나눈 이유** — 공통 모듈이 나중에 `V3` 를 추가해도 이미 `V20` 까지
> 실행한 서비스에서 순서가 꼬이지 않게 하기 위함입니다.
> 그래서 config 1계층에 `spring.flyway.out-of-order: true` 가 켜져 있습니다.

<br><br>

---

### 1-4. 잘 붙었는지 확인

**기동 로그에서 봅니다.**

```
Flyway: Successfully applied N migrations       V1 · V2 가 실행됨
Tomcat started on port 8084
```

**API 를 하나 불러 봅니다.**

```bash
curl http://localhost:8084/api/v1/nonexistent
```

```json
{ "code": "RESOURCE_NOT_FOUND", "message": "요청하신 경로를 찾을 수 없습니다.",
  "data": null, "traceId": "6a97..." }
```

**이 응답이 나오면 세 가지가 한 번에 확인됩니다.**

```
CommonApiResponse       형태가 맞음
GlobalExceptionHandler  예외 처리가 붙음
TraceIdResponseAdvice   traceId 가 채워짐
```

<br><br>

---

## 2. 자동 설정 6개

**서비스가 아무것도 등록하지 않아도 켜집니다.** 클래스패스에 무엇이 있는지를 보고
스프링이 판단합니다.

```
클래스패스에 무엇이 있나          →  어느 자동 설정이 켜지나

서블릿 웹 + spring-webmvc         ──▶  CommonWebAutoConfiguration
서블릿 웹 + spring-security       ──▶  CommonSecurityAutoConfiguration
spring-data-jpa                   ──▶  CommonJpaAutoConfiguration
spring-data-jpa + spring-kafka    ──▶  CommonMessagingAutoConfiguration
spring-kafka                      ──▶  CommonKafkaAutoConfiguration
(조건 없음)                        ──▶  CommonAsyncAutoConfiguration


DB 를 쓰는 서비스        6개 전부 켜짐        auth · user · pet · place · policy ...
무상태 서비스            Web · Security · Async 만    verdict · congestion · route
게이트웨이               ⛔ 애초에 안 씀 (플랫폼 3개)
```

<br><br>

---

### 2-1. 여섯 개가 하는 일

| 자동 설정 | 조건 | 등록하는 Bean |
|---|---|---|
| `CommonWebAutoConfiguration` | 서블릿 웹 + `ResponseBodyAdvice` | `GlobalExceptionHandler` · `TraceIdResponseAdvice` |
| `CommonSecurityAutoConfiguration` | 서블릿 웹 + `SecurityFilterChain` | `SecurityFilterChain` · `CustomSecurityExceptionHandler` · `AuthenticationManager` |
| `CommonJpaAutoConfiguration` | `JpaRepository` | `AuditorProvider` + `@EnableJpaAuditing` |
| `CommonMessagingAutoConfiguration` | `JpaRepository` + `KafkaTemplate` | `OutboxEventRecorder` · `OutboxPublisher` · `OutboxCommitListener` · `OutboxRelay` · `InboxProcessor` + `@EnableScheduling` |
| `CommonKafkaAutoConfiguration` | `KafkaTemplate` | `RecordMessageConverter` · `KafkaSecurityInterceptor` · `DefaultErrorHandler` |
| `CommonAsyncAutoConfiguration` | **없음** | `@EnableAsync` |

---

**`TraceIdResponseAdvice` 만 조건이 하나 더 있습니다.**

```java
@ConditionalOnClass(name = "io.micrometer.tracing.Tracer")
```

추적 라이브러리가 없으면 그 빈만 안 만들어집니다. `service-template` 에는
`spring-boot-starter-zipkin` 이 있어 항상 켜집니다.

<br><br>

---

### 2-2. 조건 판단에 `JpaRepository` 를 쓰는 이유

**`EntityManager` 로 판단하면 안 됩니다.**

```
무상태 서비스가 JPA 스타터를 지움
        │
        ├── spring-data-jpa          ✅ 함께 사라짐
        └── jakarta.persistence-api  ⛔ 남아 있음
                    ▲
                    └── hibernate-spatial 이 hibernate-core 를 거쳐 전이로 끌고 옴
                        (좌표 타입을 쓰는 서비스가 있어 지울 수 없음)
```

**`EntityManager` 로 판단하면 조건이 참이 되어 무상태 서비스에서 JPA 설정이 켜지고,
`entityManagerFactory` 빈을 못 찾아 기동에 실패합니다.**

---

**문자열로 쓰는 이유입니다.**

```java
@ConditionalOnClass(name = "org.springframework.data.jpa.repository.JpaRepository")
                    ▲
                    └── 클래스 리터럴로 쓰면 조건 평가 시점에 로딩을 시도해
                        NoClassDefFoundError 가 날 수 있음
```

스프링 자체 자동 설정도 이 패턴을 씁니다.

<br><br>

---

### 2-3. 서비스가 같은 타입을 정의하면 물러납니다

**모든 `@Bean` 에 `@ConditionalOnMissingBean` 이 붙어 있습니다.**

```
서비스가 자기 SecurityFilterChain 을 정의
        │
        └──▶  공통 모듈의 것이 통째로 물러남
                  ⚠ 함께 사라지는 것들을 서비스가 다시 넣어야 함
```

**auth 가 그 사례입니다.** 로그인·가입을 열어야 해서 자기 체인을 정의했고,
공통 체인이 하던 일을 전부 다시 넣었습니다.

| 함께 사라지는 것 | 빠뜨리면 |
|---|---|
| `HeaderAuthenticationFilter` 등록 | 인증이 필요한 경로가 전부 401 |
| `/api/v1/admin/**` → `hasRole("ADMIN")` | 관리자 API 가 열림 |
| `/internal/**` · `/actuator/**` permitAll | 헬스체크가 401 |
| `CustomSecurityExceptionHandler` 연결 | 401·403 이 공통 형식이 아니게 됨 |

> **`@ConditionalOnMissingBean` 은 자동 설정 클래스에서만 신뢰할 수 있습니다.**
> 일반 `@Configuration` 은 사용자 빈과의 처리 순서가 보장되지 않아 조건이 뒤집힐 수
> 있습니다. 그래서 여섯 개가 전부 `@AutoConfiguration` 입니다.

<br><br>

---

### 2-4. 무상태 서비스에서 안 켜지는 것

`verdict` · `congestion` · `route` 가 JPA 스타터를 지우면 **셋이 함께 꺼집니다.**

| 못 쓰게 되는 것 | 대신 |
|---|---|
| `BaseEntity` · JPA Auditing | 엔티티가 없으므로 필요 없습니다 |
| `OutboxEventRecorder` | **무상태 서비스는 이벤트를 발행하지 않습니다** |
| `InboxProcessor` | 이벤트로 하는 일이 캐시 삭제뿐이라 중복이 문제되지 않습니다 |

**그대로 쓸 수 있는 것입니다.**

```
CommonApiResponse · PageResponse      응답 형식
ErrorCode · CustomException           에러 코드
GlobalExceptionHandler                예외 처리
@CurrentUser · 보안 필터               인증
EventEnvelope · DomainEvent           이벤트 봉투 (소비할 때 필요)
```

> **`EventEnvelope` 에 조건을 안 건 이유** — verdict 도 이벤트를 받아 캐시를 지우므로
> 봉투를 역직렬화해야 합니다. **순수 자바라 JPA 와 무관합니다.**

<br><br>

---

## 3. 무엇이 들어 있나

```
com.pawtrail.common
│
├── config/                              자동 설정 6개 — 2장
│   └── Common*AutoConfiguration          조건에 맞으면 Bean 을 올림
│
├── response/                            응답 형식
│   ├── CommonApiResponse                 {code, message, data, traceId}
│   ├── PageResponse                      목록 응답의 data 안쪽
│   └── TraceIdResponseAdvice             응답 직전에 traceId 를 채움
│
├── exception/                           에러 코드 규약
│   ├── ErrorCode                         인터페이스 — 각 서비스가 자기 enum 으로 구현
│   ├── CommonErrorCode                   전 서비스 공통 6개
│   ├── CustomException                   의도적으로 던지는 유일한 예외
│   └── handler/GlobalExceptionHandler    핸들러 5개
│
├── entity/BaseEntity                    모든 테이블이 상속하는 6컬럼
├── audit/AuditorProvider                지금 작업하는 주체가 누구인지
├── enums/Role                           USER · ADMIN
│
├── security/                            인증 정보 다루기
│   ├── filter/HeaderAuthenticationFilter        헤더 → SecurityContext
│   ├── handler/CustomSecurityExceptionHandler   401 · 403 을 공통 형식으로
│   ├── interceptor/RestClientAuthInterceptor    호출할 때 헤더를 실어 줌 (미배선)
│   ├── principal/CustomUserPrincipal            accountId · role
│   └── annotation/CurrentUser                   컨트롤러에서 주입받음
│
└── message/                             이벤트
    ├── DomainEvent                       발행할 이벤트가 구현하는 계약
    ├── EventEnvelope                     모든 이벤트를 감싸는 봉투
    ├── AuthContextHeaders                X-User-Id · X-User-Role 의 단일 출처
    ├── KafkaSecurityInterceptor          소비할 때 인증 정보를 복원
    │
    ├── outbox/                           "저장은 됐는데 이벤트가 안 나갔다" 를 막음
    │   ├── OutboxMessage · OutboxRepository
    │   ├── OutboxEventRecorder            *서비스가 부르는 발행 입구
    │   ├── OutboxPublisher                실제 카프카 전송
    │   ├── OutboxCommitListener           커밋 직후 발행
    │   └── OutboxRelay                    놓친 건 회수 (5초)
    │
    └── inbox/                            "같은 이벤트를 두 번 처리했다" 를 막음
        ├── ProcessedEvent · ProcessedEventRepository
        └── InboxProcessor

src/main/resources/
├── META-INF/spring/AutoConfiguration.imports    *없으면 아무것도 안 켜지는데 오류도 안 남
├── db/migration/common/V1__outbox.sql · V2__inbox.sql
└── logback-loki-appender.xml                    서비스의 logback 이 include 해서 씀
```

<br><br>

---

### 3-1. response — 응답 형식

```
컨트롤러가 CommonApiResponse.success(data) 를 반환
        │
        ▼
TraceIdResponseAdvice        응답 직전에 traceId 를 채움
        │                     supports() 가 true 고정
        ▼                     실제 판별은 beforeBodyWrite 의 instanceof
{ "code": "SUCCESS", "message": "...", "data": {}, "traceId": "6a97..." }


예외가 던져지면
        │
        ▼
GlobalExceptionHandler       ErrorCode 를 보고 상태와 메시지를 정함
        │
        ▼
TraceIdResponseAdvice        *실패 응답도 여기를 지남
        │
        ▼
{ "code": "PLACE_NOT_FOUND", "message": "...", "data": null, "traceId": "6a97..." }
```

---

**`CommonApiResponse` 는 필드 4개입니다.**

| 필드 | 성공 | 실패 |
|---|---|---|
| `code` | `"SUCCESS"` | 에러 코드 이름 |
| `message` | 안내 문구 | 에러 메시지 |
| `data` | 실제 값 | `null` |
| `traceId` | 자동으로 채워짐 | 자동으로 채워짐 |

**프론트는 `code !== 'SUCCESS'` 한 줄로 판단합니다.**

---

**`@JsonInclude(NON_NULL)` 을 붙이지 않습니다.**

붙이면 실패 응답의 `"data": null` 키가 사라지는데, **API 명세에 그 키가 못박혀
있습니다.**

---

**`withTraceId` 는 인스턴스 메서드입니다.**

```java
// static 으로 합치면 안 됨
public CommonApiResponse<T> withTraceId(String traceId) { ... }
```

Advice 는 **성공뿐 아니라 `GlobalExceptionHandler` 가 만든 실패 응답도 지납니다.**
static 이면 `code` 가 `"SUCCESS"` 로 덮여 **404 가 성공으로 나갑니다.**

---

**`supports()` 가 `true` 고정인 이유입니다.**

```java
public boolean supports(MethodParameter returnType, Class<?> converterType) {
    return true;      // 실제 판별은 beforeBodyWrite 의 instanceof
}
```

`returnType.getParameterType()` 은 **선언된** 반환 타입이라
`ResponseEntity<CommonApiResponse<T>>` 를 반환하는 `GlobalExceptionHandler` 가
걸러집니다. 그러면 **실패 응답에만 `traceId` 가 조용히 누락됩니다.**

---

**`PageResponse` 는 변환 함수를 받습니다.**

```java
PageResponse.from(page, PlaceOutput::from)
```

```json
{ "content": [ ], "page": { "number": 0, "size": 20, "totalElements": 0, "totalPages": 0 } }
```

**엔티티를 그대로 노출하지 않게** 하는 것이 핵심입니다.
안 넣으면 `getContent()` + 페이지 정보 조립이 **페이징 엔드포인트마다 반복되고,
필드 순서를 바꿔 넣어도 컴파일이 통과합니다.**

<br><br>

---

### 3-2. exception — 에러 코드 규약

**`ErrorCode` 는 인터페이스입니다.**

```java
public interface ErrorCode {
    HttpStatus getHttpStatus();
    String getCode();
    String getMessage();
}
```

**enum 은 상속이 안 되는데 핸들러는 하나로 받아야 하기 때문**입니다.
각 서비스가 자기 enum 으로 구현합니다.

---

**`CommonErrorCode` 6개 — 전 서비스 공통만 담습니다.**

| 코드 | HTTP | 언제 |
|---|---|---|
| `VALIDATION_FAILED` | 400 | `@Valid` 실패 · 타입 불일치 |
| `AUTHENTICATION_FAILED` | 401 | 인증 실패 |
| `ACCESS_DENIED` | 403 | 권한 없음 |
| `RESOURCE_NOT_FOUND` | 404 | 컨트롤러가 없는 주소 |
| `INTERNAL_ERROR` | 500 | 그 밖의 예외 |
| `EXTERNAL_API_ERROR` | 502 | 외부 API 호출 실패 |

> **도메인 개념을 넣지 않습니다.** `PLACE_NOT_FOUND` 가 여기 있으면 verdict 도
> 그것을 보게 됩니다.

---

**`CustomException` 하나뿐이고 하위 클래스가 없습니다.**

```java
throw new CustomException(PlaceErrorCode.PLACE_NOT_FOUND);
```

**상태값이 이미 `ErrorCode` 에 있어** 예외 클래스를 상태별로 나누면
**두 번째 진실의 원천이 되고 둘이 어긋나도 아무도 모릅니다.**

> `super()` 에 `code: message` 를 조립해 **로그의 스택트레이스 첫 줄에 코드가 남습니다.**
> 핸들러 밖으로 새는 경우(카프카 컨슈머 · `@Async`)의 안전망입니다.

---

**`getCode()` 는 반드시 `name()` 을 그대로 반환합니다.**

```java
@Override
public String getCode() {
    return name();
}
```

**상수 이름이 곧 응답 `code` 이자 API 계약**인데 **규칙을 어겨도 컴파일러가
잡지 못합니다.**

---

**핸들러 5개입니다.**

| | 잡는 것 | 응답 | 로그 |
|---|---|---|---|
| 1 | `CustomException` | `ErrorCode` 의 상태 | `warn` |
| 2 | `MethodArgumentNotValidException` | 400 + **필드별 오류 배열** | `warn` |
| 3 | `MethodArgumentTypeMismatchException` | 400 | `warn` |
| 4 | `NoResourceFoundException` | 404 | `warn` |
| 5 | `Exception` | 500 | **`error` + 스택트레이스** |

**4번이 없으면 오타 난 URL 하나가 500 으로 나갑니다.** 그러면 5번이
스택트레이스를 찍어 **Loki 에서 진짜 오류를 못 찾습니다.**

---

**401·403 은 여기로 오지 않습니다.**

```
시큐리티 필터  ──▶  DispatcherServlet  ──▶  컨트롤러
      ▲                                        ▲
      │                                        │
   401·403 이 여기서 결정됨              @RestControllerAdvice 가 잡는 범위
   → CustomSecurityExceptionHandler 가 처리
```

**검증 때 401 이 `Content-Length: 0` 으로 바디 없이 나간 적이 있어**
그 핸들러를 만들었습니다.

<br><br>

---

### 3-3. entity · audit — 감사 컬럼

**`BaseEntity` 6컬럼입니다.**

| 컬럼 | 채우는 것 | `updatable` |
|---|---|---|
| `createdAt` · `createdBy` | JPA Auditing | `false` |
| `updatedAt` · `updatedBy` | JPA Auditing | 기본값 |
| `deletedAt` · `deletedBy` | **수동** — `delete(auditorProvider.current())` | 기본값 |

> ⚠ **`updatedBy` 에 `updatable = false` 를 붙이면 안 됩니다.** UPDATE 문에서 빠져
> `@LastModifiedBy` 가 값을 채워도 **DB 에 안 나가고 오류도 로그도 없습니다.**

---

**`AuditorProvider` 는 세 갈래입니다.**

```
인증 없음 (배치)                    app.auditor.system-name     ingest-batch
익명 (AnonymousAuthenticationToken)  app.auditor.system-name     SYSTEM
인증됨                              principal.accountId
```

**`current()` 가 인스턴스 메서드여야 합니다.** static 이면 설정으로 주입받은
`systemName` 을 못 써서 **배치의 `ingest-batch` 와 `SYSTEM` 이 갈립니다.**

> `Optional.empty()` 를 반환하지 않습니다. 반환하면 컬럼이 null 로 남고
> `nullable = false` 에서 터집니다.

---

**소프트 딜리트의 진짜 논점은 조회입니다.**

```
모든 조회에 WHERE deleted_at IS NULL 이 붙어야 함
        │
        └── 하나만 빠뜨리면 삭제된 데이터가 노출됨
```

**아직 방식을 정하지 않았습니다.** 후보는 `@SQLRestriction` · 리포지터리마다 명시 ·
QueryDSL 공통 조건이고, **실제 조회를 짜는 서비스에서 정합니다.**

<br><br>

---

### 3-4. security — 인증 정보

```
게이트웨이  ──▶  X-User-Id · X-User-Role 헤더
                        │
                        ▼
        HeaderAuthenticationFilter        헤더를 읽어 SecurityContext 를 채움
                        │                 헤더가 없으면 심지 않고 통과
                        ▼
        CustomUserPrincipal(accountId, role)
                        │
                        ▼
        @CurrentUser 로 컨트롤러가 꺼냄
```

**`SecurityContext`** 는 *"이 요청을 보낸 사람이 누구인가"* 를 요청이 끝날 때까지 들고 있는
자리입니다. 필터가 헤더를 읽어 거기 채워 두면 **컨트롤러는 `@CurrentUser` 로 꺼내기만 합니다.**

```java
@GetMapping
public CommonApiResponse<List<PetOutput>> getMyPets(@CurrentUser CustomUserPrincipal principal) {
    return CommonApiResponse.success(petService.findByAccount(principal.accountId()));
}
```

---

**기본 보안 체인의 규칙 셋입니다.**

| 경로 | 규칙 |
|---|---|
| `/internal/**` · `/actuator/**` | permitAll |
| `/api/v1/admin/**` | **`hasRole("ADMIN")`** |
| 그 외 | 인증 필수 |

**관리자 보호를 공통 모듈에 둔 이유**는 관리자 기능이 **여러 서비스에 흩어져
있기 때문**입니다. 각자 막게 하면 **한 곳만 빠뜨려도 그 서비스가 열립니다.**

---

**`hasRole("ADMIN")` 이 동작하는 방식입니다.**

```
게이트웨이가 넣음     X-User-Role: ADMIN        접두사 없음
필터가 만듦          "ROLE_" + role   →  ROLE_ADMIN
hasRole("ADMIN")     내부적으로 ROLE_ADMIN 을 찾음
```

**접두사 규칙을 바꾸면 관리자 경로가 403 만 반환하고 원인이 안 드러납니다.**

---

**필터를 `@Component` 로 두지 않았습니다.**

`@Component` 면 Boot 가 **시큐리티 체인과 서블릿 전역 필터 양쪽에 등록**해
요청마다 두 번 실행됩니다. 자동 설정에서 `new` 로 만들어 체인에만 붙입니다.

---

**`CustomUserPrincipal` 이 `AuthenticatedPrincipal` 을 구현합니다.**

```java
public record CustomUserPrincipal(UUID accountId, Role role) implements AuthenticatedPrincipal {
    @Override public String getName() { return accountId.toString(); }
}
```

**아무것도 구현하지 않으면 `getName()` 이 `toString()` 을 반환해
`created_by` 에 객체 해시가 조용히 쌓입니다.**

> `UserDetails` 를 안 쓴 이유 — 게이트웨이가 이미 검증하고 헤더만 넘기는 모델이라
> **`getPassword()` 와 계정 상태 플래그 4개가 전부 무의미**합니다.

<br><br>

---

### 3-5. message/outbox — 이벤트를 안전하게 보내기

**트랜잭션**이란 *"여러 쓰기를 한 덩어리로 — 전부 되거나 전부 안 되거나"* 입니다.
계정 INSERT 와 outbox INSERT 를 한 트랜잭션에 넣으면 **둘 중 하나만 남는 일이 없습니다.**

```
서비스 트랜잭션
  │
  ├──▶  비즈니스 데이터 INSERT
  │
  └──▶  outboxEventRecorder.record(이벤트)      *한 줄
              │
              ├──▶  EventEnvelope.of(이벤트)          봉투 생성
              ├──▶  JsonMapper 로 직렬화
              ├──▶  outbox 행 INSERT                 ← 같은 트랜잭션
              └──▶  스프링 이벤트 발행
                        │
  커밋 ────────────────┘
    │
    ▼
  OutboxCommitListener      @Async · @TransactionalEventListener(AFTER_COMMIT)
    │
    └──▶  OutboxPublisher.publish(id)           @Transactional(REQUIRES_NEW)
              │
              ├──▶  FOR UPDATE SKIP LOCKED 로 행을 잠금
              ├──▶  kafkaTemplate.send()
              │        성공 ──▶  published_at 기록
              │        실패 ──▶  retry_count +1 · last_error 기록
              ▼
  OutboxRelay               5초마다 · 미발행 건을 주움 (안전망)
    │
    ├── retry_count < 10   다시 publish
    └── retry_count >= 10  포기. 관리자 재발행 API 로만 되살릴 수 있음
```

---

**`OutboxEventRecorder` 가 없으면 서비스가 5단계를 손으로 해야 합니다.**

```
① EventEnvelope.of(event)
② JsonMapper 로 직렬화
③ OutboxMessage.create(...)
④ outboxRepository.save()
⑤ applicationEventPublisher.publishEvent(saved)     ← 빠뜨리면 오류 없이 5초 지연
```

**⑤를 빠뜨리면 에러가 안 나고 지연만 생겨 알아채기 어렵습니다.**

---

**`@Transactional(propagation = MANDATORY)` 입니다.**

```java
outboxEventRecorder.record(new PlaceUpdatedEvent(placeId));
```

트랜잭션 없이 부르면 **호출 시점에 즉시 예외**가 납니다.

```
IllegalTransactionStateException: No existing transaction found
```

**Outbox 의 전제(비즈니스 데이터와 이벤트가 원자적)를 규약이 아니라
구조로 강제하는 자리입니다.**

---

**`OutboxRelay` 는 한 인스턴스에서만 켭니다.**

```yaml
app:
  outbox:
    relay:
      enabled: true      # 이벤트를 발행하는 서비스만. 다인스턴스면 하나만
```

여러 Relay 가 돌면 **서로 "앞에 미발행 건이 없다" 고 판단해 순서 보장이 깨집니다.**

---

**`FOR UPDATE SKIP LOCKED` 로 중복 발행을 막습니다.**

```
리스너와 Relay 가 같은 행을 동시에 집을 수 있음
        │
        └── 실제로 겪었음 — 첫 발행에서 카프카 프로듀서 생성에 0.6초가 걸리는 사이
            Relay 폴링(5초)이 끼어들어 같은 이벤트가 2건 나감
```

`OutboxPublisher` 가 행을 잠그고 시작하므로 **뒤엣것은 조회 결과가 비어 건너뜁니다.**

---

**재시도 10회를 넘기면 Relay 가 포기합니다.**

```
retry_count >= 10
        │
        ├── Relay 조회에서 빠짐            published_at 이 NULL 인 채로 영원히 남음
        └── 되살릴 수단은 관리자 재발행 API 하나뿐
                findGivenUpMessages(Pageable) 로 목록을 뽑고
                OutboxPublisher.publish(id) 를 직접 호출
```

> **각 서비스가 그 API 를 만들어야 합니다.** 공통 모듈은 조회 메서드까지만 줍니다.
> `service-template` README 7-7 에 만드는 법이 있습니다.

<br><br>

---

### 3-6. message/inbox — 같은 이벤트를 두 번 처리하지 않기

```
카프카에서 수신
  │
  └──▶  inboxProcessor.processOnce(eventId, topic, 로직)
              │
              ├──▶  existsById(eventId)          흔한 경우를 싸게 막음
              │        있음 ──▶  건너뜀 (이미 처리)
              │
              ├──▶  ProcessedEvent 저장          PK 충돌이 드문 경쟁을 확실히 막음
              │        충돌 ──▶  예외 → 재시도에서 건너뜀
              │
              └──▶  비즈니스 로직 실행            *같은 트랜잭션

  둘을 한 트랜잭션에 묶는 이유
    따로 커밋하면   "로직 성공 + 기록 실패"  →  중복 처리
                   "기록 성공 + 로직 실패"  →  영영 건너뜀
```

---

**`ProcessedEvent` 가 `Persistable` 을 구현합니다.**

```java
@Override public boolean isNew() { return true; }
```

**ID 가 발행자에게서 온 값**이라 `save()` 시점에 null 이 아니고,
스프링 데이터가 `isNew() = false` 로 판단해 **`persist()` 가 아니라 `merge()`** 를
부릅니다.

```
merge 는 SELECT 후 행이 있으면 UPDATE
        │
        └── PK 충돌이 안 나고 조용히 UPDATE → 비즈니스 로직이 중복 실행
              "PK 가 드문 경쟁을 확실히 막는다" 는 설계 근거가 무너짐
```

---

**예외를 잡지 않고 그대로 던집니다.**

```
컨슈머 밖으로 나가야 DefaultErrorHandler 가 동작
        1초 → 2초 → 4초 로 3회 재시도
        그래도 실패하면 {원본토픽}.dlq 로 보내고 넘어감
```

**`try-catch` 로 삼키면 실패한 이벤트가 성공으로 처리되어 사라집니다.**

---

**`processed_event` 는 무한히 쌓입니다.**

지우는 로직이 없습니다. 지금 규모에서는 무방하고 **`(processed_at)` 인덱스를
미리 만들어 두었습니다.**

<br><br>

---

### 3-7. message — 봉투와 계약

**`DomainEvent` 는 메서드 셋입니다.**

```java
public interface DomainEvent {
    @JsonIgnore String getTopic();
    @JsonIgnore String getAggregateType();
    @JsonIgnore String getAggregateId();
}
```

**`@JsonIgnore` 를 인터페이스 선언에 붙였습니다.**

```
안 붙이면
    자바빈 게터라 Jackson 이 data 안에도 직렬화함
        │
        └── 확정 payload 명세에 없는 필드 3개가 들어가고
            소비자 역직렬화가 터질 수 있음
```

> **애노테이션이 구현체에 상속되는 것은 auth 첫 회원가입에서 실물로 확인됐습니다.**

---

**`EventEnvelope` 의 구성입니다.**

```json
{
  "eventId": "01a0...",          중복 판단 키 (UUID v7)
  "eventType": "place.updated",
  "occurredAt": "2026-09-01T14:22:10",
  "aggregateType": "Place",
  "aggregateId": "01a0...",      파티션 키
  "data": { }                    *각 서비스가 따로 정의
}
```

**`data` 안쪽 DTO 는 공통 모듈에 두지 않습니다.** 두면 발행하는 쪽이 필드를
추가할 때 **받는 쪽까지 다시 배포해야 합니다.**

---

**받는 쪽은 자기 DTO 를 만듭니다.**

```java
@JsonIgnoreProperties(ignoreUnknown = true)       // 필수
public record PlaceUpdatedMessage(UUID placeId) { }
```

**제네릭에 타입 제약이 없어** 받는 쪽 DTO 는 `DomainEvent` 를 구현하지 않아도 됩니다.

---

**`RecordMessageConverter` 가 봉투를 타입 그대로 받게 해 줍니다.**

```java
@KafkaListener(topics = "place.updated", groupId = "${spring.application.name}")
public void onPlaceUpdated(EventEnvelope<PlaceUpdatedMessage> envelope) { ... }
```

**없으면** `value-deserializer` 가 `StringDeserializer` 라 리스너에 String 이 들어와
`MessageConversionException` 이 납니다.

> **발행 쪽과 같은 `JsonMapper` 를 넘깁니다.** 다른 매퍼면 봉투의
> `LocalDateTime occurredAt` 표현이 **나갈 때와 들어올 때 어긋날 수 있습니다.**

---

**`AuthContextHeaders` 가 헤더 이름의 단일 출처입니다.**

```
X-User-Id · X-User-Role
        │
        ├── HeaderAuthenticationFilter        HTTP 로 받을 때
        ├── RestClientAuthInterceptor         서비스를 부를 때 (미배선)
        ├── KafkaSecurityInterceptor          이벤트를 받을 때
        └── ⚠ 게이트웨이가 자기 저장소에 따로 적음   ← 4번째 사본. common 을 안 씀
```

**어긋나면 필터가 헤더를 못 찾아 인증 없이 통과시키고, 그 뒤 경로 규칙에서 막혀
401 로만 나타납니다.**

<br><br>

---

## 4. 쓰는 법

**자주 쓰는 여섯 가지입니다.** 서비스 코드에서 실제로 이렇게 씁니다.

<br><br>

---

### 4-1. 응답 감싸기

```java
@GetMapping("/{placeId}")
public CommonApiResponse<PlaceOutput> getPlace(@PathVariable UUID placeId) {
    return CommonApiResponse.success(placeService.getPlace(placeId));
}
```

**목록은 `PageResponse` 를 함께 씁니다.**

```java
@GetMapping
public CommonApiResponse<PageResponse<PlaceOutput>> getPlaces(Pageable pageable) {
    Page<Place> page = placeService.search(pageable);
    return CommonApiResponse.success(PageResponse.from(page, PlaceOutput::from));
}
```

**상태 코드를 바꾸려면 `ResponseEntity` 로 감쌉니다.**

```java
return ResponseEntity.status(HttpStatus.CREATED)
        .body(CommonApiResponse.success(output));
```

> `traceId` 는 신경 쓰지 않습니다. **응답 직전에 자동으로 채워집니다.**
>
> 반환 데이터가 없으면 `CommonApiResponse.<Void>success(null)` 입니다.
> 무인자 버전은 만들지 않았습니다.

<br><br>

---

### 4-2. 에러 코드 만들고 던지기

**서비스마다 자기 enum 을 만듭니다.**

```java
package com.pawtrail.place.domain.exception;

import com.pawtrail.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum PlaceErrorCode implements ErrorCode {

    PLACE_NOT_FOUND(HttpStatus.NOT_FOUND, "장소를 찾을 수 없습니다."),
    PLACE_ALREADY_CLOSED(HttpStatus.CONFLICT, "이미 폐업 처리된 장소입니다.");

    private final HttpStatus httpStatus;
    private final String message;

    // 상수 이름이 그대로 응답 code 가 됨
    // 다른 문자열을 돌려주면 프론트가 보는 값과 코드에 적힌 이름이 갈림
    @Override
    public String getCode() {
        return name();
    }
}
```

**던질 때는 `CustomException` 하나만 씁니다.**

```java
Place place = placeRepository.findById(placeId)
        .orElseThrow(() -> new CustomException(PlaceErrorCode.PLACE_NOT_FOUND));
```

> **컨트롤러에 `try-catch` 를 쓰지 않습니다.** `GlobalExceptionHandler` 가 잡습니다.
>
> **예외 클래스를 새로 만들지 않습니다.** 상태와 메시지가 두 곳에 생깁니다.

---

**언제 공통 코드를 쓰나**

| | 쓰는 것 |
|---|---|
| 어느 서비스에서나 같은 뜻 | `CommonErrorCode` |
| 이 도메인만의 실패 | 자기 `ErrorCode` |

> **`RESOURCE_NOT_FOUND` 로 뭉뚱그리지 않습니다.** 프론트가 *"무엇을"* 못 찾았는지
> 알아야 화면을 다르게 보여 줄 수 있습니다.

---

**계약 위반은 `CustomException` 이 아닙니다.**

```java
// 우리 코드가 잘못 부른 것 — 400 이 아니라 500 이 맞음
if (passwordHash == null) {
    throw new IllegalArgumentException("LOCAL 계정은 비밀번호가 필요합니다");
}
```

*"서비스에서 던질 유일한 예외는 `CustomException`"* 규칙은 **비즈니스 예외**를
말하는 것이고, **계약 위반은 성격이 다릅니다.**

<br><br>

---

### 4-3. 로그인한 사용자 꺼내기

```java
@GetMapping
public CommonApiResponse<List<PetOutput>> getMyPets(
        @CurrentUser CustomUserPrincipal principal) {

    return CommonApiResponse.success(petService.findByAccount(principal.accountId()));
}
```

`CustomUserPrincipal` 은 둘만 가집니다.

```java
record CustomUserPrincipal(UUID accountId, Role role)
```

> **게이트웨이가 헤더로 넣어 준 값**이라 토큰을 파싱하는 코드가 서비스에 없습니다.

<br><br>

---

### 4-4. 엔티티 만들기

```java
@Entity
@Table(name = "place")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Place extends BaseEntity {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(nullable = false, length = 200)
    private String name;
}
```

**삭제만 수동입니다.**

```java
place.delete(auditorProvider.current());
```

> `auditorProvider.current()` 를 쓰는 이유는 생성·수정과 **같은 출처의 값**을
> 넣기 위해서입니다.
>
> **마이그레이션 스크립트에도 6컬럼이 있어야 합니다.** `ddl-auto: validate` 라
> 어긋나면 기동이 실패합니다.

<br><br>

---

### 4-5. 이벤트 발행하기

**이벤트를 정의합니다.**

```java
package com.pawtrail.place.domain.event.payload;

import com.pawtrail.common.message.DomainEvent;
import java.util.UUID;

public record PlaceUpdatedEvent(UUID placeId) implements DomainEvent {

    @Override
    public String getTopic() {
        // infra 의 create-topics.sh 에 같은 이름이 있어야 함
        return "place.updated";
    }

    @Override
    public String getAggregateType() {
        return "Place";
    }

    @Override
    public String getAggregateId() {
        return placeId.toString();
    }
}
```

**발행은 한 줄입니다.**

```java
@Transactional                                    // 반드시 트랜잭션 안에서
public void updatePlace(UUID placeId, PlaceUpdateInput input) {

    Place place = placeRepository.findById(placeId)
            .orElseThrow(() -> new CustomException(PlaceErrorCode.PLACE_NOT_FOUND));

    place.update(input.name());

    outboxEventRecorder.record(new PlaceUpdatedEvent(placeId));
}
```

> **담는 값을 최소로 합니다.** 받는 쪽이 `/internal` 로 다시 읽을 수 있는 값은
> 싣지 않고, **개인정보는 더욱 싣지 않습니다.**

<br><br>

---

### 4-6. 이벤트 받기

**소비 전용 DTO 를 만듭니다.**

```java
package com.pawtrail.search.infrastructure.message.kafka.consumer.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)       // 필수
public record PlaceUpdatedMessage(UUID placeId) { }
```

| 규칙 | 왜 |
|---|---|
| `@JsonIgnoreProperties` 를 붙임 | 없으면 **발행 쪽이 필드를 추가하는 순간 깨지고** 배포 순서가 묶임 |
| `DomainEvent` 를 구현하지 않음 | 받는 쪽에는 토픽·집합체 값이 의미가 없음 |
| 실제로 쓰는 필드만 | 나머지는 무시됨 |

**리스너는 봉투를 타입 그대로 받습니다.**

```java
@KafkaListener(topics = "place.updated", groupId = "${spring.application.name}")
public void onPlaceUpdated(EventEnvelope<PlaceUpdatedMessage> envelope) {
    inboxProcessor.processOnce(
            envelope.eventId(),
            envelope.eventType(),
            () -> searchIndexService.reindex(envelope.data().placeId())
    );
}
```

**지켜야 할 것 셋입니다.**

```
1  토픽 문자열이 발행 쪽 getTopic() 과 정확히 같아야 함
     어긋나도 오류가 안 나고 이벤트만 오지 않음

2  processOnce 로 감쌈
     같은 이벤트를 두 번 받아도 한 번만 처리됨
     재시도·리밸런싱으로 두 번 오는 것은 정상적인 일

3  예외를 잡지 않고 그대로 던짐
     컨슈머 밖으로 나가야 재시도와 DLQ 가 동작
     try-catch 로 삼키면 실패한 이벤트가 성공으로 처리되어 사라짐
```

<br><br>

---

## 5. 버전을 올리고 배포하기

**이 모듈을 고칠 때만 봅니다.** 자주 있는 일이 아닙니다.

```
① 코드를 고치고 build.gradle 의 version 을 올림
        │
        ▼
② ./gradlew publishToMavenLocal              *로컬에 먼저 넣음
        │
        ▼
③ 소비 서비스에서 검증
        build.gradle 의 repositories 맨 앞에 mavenLocal() 임시 추가
        gradle.properties 의 commonVersion 을 새 버전으로
        ./gradlew build 가 통과하는지
        │
        ▼
④ ./gradlew publish                          GitHub Packages 로
        │
        ▼
⑤ mavenLocal() 을 지우고 다시 빌드            *순서 주의
        │
        ▼
⑥ 버전 표기를 여러 곳에 반영
```

<br><br>

---

### 5-1. ②③ 을 건너뛰지 않습니다

```
GitHub Packages 는 같은 버전을 덮어쓸 수 없음
        │
        └── publish 한 번이 버전 하나를 영구히 소모함

로컬 저장소는 몇 번이든 다시 넣을 수 있음
        │
        └── 잘못된 것을 올려 버전을 버리는 대신 여기서 먼저 확인
```

**`mavenLocal()` 은 받아 쓰는 쪽에 넣습니다.**

```groovy
// auth-service/build.gradle
repositories {
    mavenLocal()        // ← 임시. publish 뒤에 지움
    mavenCentral()
    maven { ... }
}
```

> **common 에 넣으면 아무 효과가 없습니다.** 자기가 자기를 안 내려받기 때문입니다.
> 2026.9.1 에 실제로 헷갈린 적이 있습니다.

---

**⑤ 의 순서를 지킵니다.**

```
mavenLocal() 을 먼저 지움
        │
        └── ghcr 에 아직 없는 버전을 못 찾아 빌드가 깨짐
              Could not find com.pawtrail:common:0.0.10
```

**publish 가 끝난 뒤에 지웁니다.**

---

**소비 쪽 `commonVersion` 도 함께 올려야 검증이 됩니다.**

옛 값이면 **ghcr 에서 옛 버전을 받아 그냥 통과합니다.**

<br><br>

---

### 5-2. publish 할 때 걸리는 것

**환경변수가 터미널 세션마다 사라집니다.**

```powershell
$env:GPR_USER  = "<GitHub 아이디>"
$env:GPR_TOKEN = (Get-Content C:\Tour_Prj\GPR_TOKEN.txt).Trim()
./gradlew publish
```

```bash
export GPR_USER=<GitHub 아이디>
export GPR_TOKEN=$(cat ~/GPR_TOKEN.txt | tr -d '\n')
./gradlew publish
```

| 걸리는 것 | 증상 |
|---|---|
| 새 터미널에서 실행 | `401 Unauthorized` |
| `.Trim()` 을 안 함 | **파일 끝 개행이 값에 섞여 또 401** |
| 토큰에 `write:packages` 가 없음 | 401 |

> 매번 넣기 성가시면 **OS 환경변수**로 둡니다. 터미널 재시작이 필요합니다.

<br><br>

---

### 5-3. ⑥ 버전 표기를 반영할 곳

```
service-template/gradle.properties        commonVersion
service-template/README.md                1-4-2 · 7-1 · 6-4 표      세 곳
<이미 만든 서비스>/gradle.properties        템플릿은 복사 후 연결이 끊기므로 각각
```

> ⚠ **실제로 빠뜨린 적이 있습니다.** `0.0.7` 로 올릴 때 README 를 한 곳만 고쳐
> **나머지 둘이 `0.0.4` 로 남아 있었고 `0.0.8` 에서 발견했습니다.**

---

**서비스가 버전을 안 올리면 옛것으로 계속 빌드됩니다.**

**컴파일은 정상적으로 되기 때문에 알아채기 어렵습니다.**
공통 모듈이 바뀌었다는 공지를 받으면 **이 값부터 확인합니다.**

<br><br>

---

### 5-4. jar 안을 확인합니다

```bash
jar tf build/libs/common-0.0.9.jar | grep -E "AutoConfiguration.imports|db/migration|Common.*AutoConfiguration.class"
```

**들어가야 하는 것입니다.**

```
META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
com/pawtrail/common/config/Common*AutoConfiguration.class          6개
db/migration/common/V1__outbox.sql
db/migration/common/V2__inbox.sql
com/pawtrail/common/entity/QBaseEntity.class                       *QueryDSL 생성물
```

> **`imports` 파일이 없으면 자동 설정이 하나도 안 켜지는데 오류도 안 납니다.**
>
> **`QBaseEntity.class` 도 확인합니다.** 없으면 `BaseEntity` 를 상속하는 서비스에서
> 컴파일이 실패합니다. `0.0.6` 에서 실제로 겪었습니다.

<br><br>

---

### 5-5. 라이브러리라 build.gradle 이 다릅니다

```groovy
// 실행 가능 jar 를 만들지 않음
tasks.named('bootJar') { enabled = false }
tasks.named('jar')     { enabled = true  }
```

> ⛔ **다른 레포와 정반대입니다.** 서비스 레포는 `jar` 를 끄고 `bootJar` 만 켭니다.
> 여기서 바꾸면 **publish 산출물이 사라집니다.**

---

**의존성이 `compileOnly` 입니다.**

```groovy
compileOnly 'org.springframework.boot:spring-boot-starter-data-jpa'
```

| | `compileOnly` | `api` |
|---|---|---|
| 소비 서비스에 전이 | 안 됨 | 됨 |
| 무상태 서비스 | JPA 가 안 딸려 옴 ✅ | **JPA 가 딸려 와 조건이 항상 참** ⛔ |

`api` 로 하면 verdict 에 JPA 가 딸려 와 `@ConditionalOnClass` 가 참이 되고
**DataSource 가 없어 기동에 실패합니다.**

<br><br>

---

## 6. 막히기 쉬운 자리

<br><br>

---

### 6-1. 자동 설정이 안 켜질 때

| 증상 | 원인 |
|---|---|
| `entityManagerFactory` 빈을 못 찾음 | **조건 오판.** DB 없는 서비스인데 JPA 자동 설정이 켜짐 |
| 아무 Bean 도 안 올라오는데 오류도 없음 | **jar 에 `AutoConfiguration.imports` 가 안 들어감** |
| 응답이 공통 형식이 아님 | `CommonWebAutoConfiguration` 이 안 켜짐. 서블릿 웹인지 확인 |
| `/api/v1/admin/**` 이 열려 있음 | **서비스가 자기 `SecurityFilterChain` 을 정의함.** 그 줄을 직접 넣어야 함 |
| 인증이 필요한 경로가 전부 401 | 같음. `HeaderAuthenticationFilter` 를 직접 등록해야 함 |
| `Using generated security password` | `AuthenticationManager` 빈이 없음. `0.0.7` 이후로는 안 나와야 정상 |

---

**`entityManagerFactory` 오류가 나면**

```
DB 를 쓰지 않는 서비스인가
        │
        ├── 예  →  build.gradle 에서 데이터 블록을 통째로 지웠는지
        │           앱 클래스의 @EntityScan · @EnableJpaRepositories 도 지웠는지
        │           (service-template README 1-5)
        │
        └── 아니오 →  spring.datasource 설정이 내려오는지
```

<br><br>

---

### 6-2. 이벤트가 안 나갈 때

| 증상 | 원인 |
|---|---|
| `IllegalTransactionStateException` | `record()` 를 **트랜잭션 없이** 불렀음 |
| outbox 행은 있는데 카프카에 없음 | 토픽이 없음 (자동 생성이 꺼져 있음) · `retry_count` 확인 |
| 같은 이벤트가 두 번 나감 | **`0.0.8` 이전 버전.** 버전을 올릴 것 |
| 발행이 5초씩 지연됨 | `OutboxCommitListener` 가 안 돎. `record()` 를 안 쓰고 손으로 저장했는지 |
| `retry_count` 가 10 인 행이 남음 | **Relay 가 포기함.** 관리자 재발행 API 로만 되살림 |

**직접 확인**

```bash
docker compose exec postgres psql -U auth_svc -d auth_db -P pager=off -c \
  "SELECT id, topic, retry_count, last_error, published_at FROM outbox ORDER BY created_at;"
```

<br><br>

---

### 6-3. 이벤트를 못 받을 때

| 증상 | 원인 |
|---|---|
| `MessageConversionException` | `RecordMessageConverter` 가 없음. 서비스가 자기 것을 정의했는지 |
| 리스너가 아예 안 불림 | **토픽 이름이 발행 쪽과 다름.** 오류가 안 나고 조용함 |
| `data` 가 `LinkedHashMap` | 소비 DTO 타입을 안 적었음 |
| 발행 쪽이 필드를 추가하자 깨짐 | 소비 DTO 에 `@JsonIgnoreProperties(ignoreUnknown = true)` 가 없음 |
| 같은 이벤트가 두 번 처리됨 | `processOnce` 로 안 감쌌음 |
| 실패한 이벤트가 사라짐 | **`try-catch` 로 삼켰음.** 예외를 그대로 던질 것 |

> **`RecordMessageConverter` 를 서비스가 정의하면 둘이 되어 둘 다 적용되지 않습니다.**
> Boot 이 `getIfUnique()` 로 집어가기 때문입니다.

<br><br>

---

### 6-4. 빌드가 안 될 때

| 증상 | 원인 |
|---|---|
| `Received status code 401` | `GPR_USER` · `GPR_TOKEN` 이 없거나 틀림 |
| `Could not find com.pawtrail:common:x.y.z` | 아직 publish 안 된 버전. 또는 `mavenLocal()` 을 먼저 지움 |
| `QBaseEntity` 를 못 찾음 | **`0.0.6` 이전 버전.** 버전을 올릴 것 |
| `com.fasterxml.jackson.databind.ObjectMapper` 가 컴파일 안 됨 | **common 에는 Jackson 3만 있음.** `JsonMapper` 를 쓸 것 |

---

**Jackson 상황입니다.**

```
common                Jackson 3 만       tools.jackson.core:jackson-databind
                      starter-webmvc → starter-jackson 이 전이로 데려옴

서비스                 Jackson 2 · 3 둘 다
                      eureka-client · config-client · resilience4j · springdoc 이 2를 데려옴
```

**주입받을 빈은 `JsonMapper` 입니다.** Boot 4 가 자동 설정하는 것이 그것이고
`@Primary` 입니다. 대체하려면 `@Bean ObjectMapper` 가 아니라 **`@Bean JsonMapper`** 여야
자동 설정이 물러납니다.

> Jackson 3 는 **언체크 예외**라 `writeValueAsString` 에 `try-catch` 가 필요 없습니다.
> 날짜는 기본이 **ISO-8601 문자열**이라 우리 명세와 설정 없이 맞습니다.

<br><br>

---

### 6-5. IntelliJ 가 내는 오탐

```
Could not autowire. No beans of type '...' found.
```

**common 은 라이브러리라 `@SpringBootApplication` 이 없어** IDE 가 컨텍스트 모델을
못 만듭니다. **빌드는 통과합니다.**

---

**`.properties` 한글이 깨질 때**

```
Settings → Editor → File Encodings
  Default encoding for properties files      UTF-8
  Transparent native-to-ascii conversion     체크 해제
```

**설정을 바꾸기 전에 그 파일을 저장하지 않습니다.** 화면만 깨진 상태에서 저장하면
**원본이 물음표로 바뀌어 복구되지 않습니다.**

<br><br>

---

## 7. 왜 이렇게 만들었나

각 항목은 **문제 → 고른 것 → 버린 것** 순서입니다.

<br><br>

---

### 7-1. 등록 방식 — 스캔이 아니라 imports

| 고름 | 버림 |
|---|---|
| `AutoConfiguration.imports` 로만 등록 | 소비 서비스가 `scanBasePackages` 에 넣기 |
| `@ConditionalOnMissingBean` 이 신뢰됨 | 일반 `@Configuration` 은 처리 순서가 안 보장돼 조건이 뒤집힘 |
| 앱 클래스에 적을 것이 줄어듦 | **14개 레포가 정확히 적어야 하는 구조** |

**auth 가 자기 `SecurityFilterChain` 을 정의하는 자리가 설계에 이미 있었고,**
그것이 성립하려면 조건이 신뢰돼야 했습니다.

> **양쪽에 걸면 안 됩니다.** 자동 설정 클래스가 스캔에 잡히면 처리 순서가 깨져
> **조건 평가가 뒤집히고** 같은 `@Configuration` 이 두 번 등록될 수 있습니다.
> 스프링 부트가 명시적으로 금하는 형태입니다.

---

**그래서 `@Component` 를 전부 `@Bean` 으로 옮겼습니다.**

```
imports 로만 등록하면
        │
        └── @Component 는 스캔이 잡는 것이라 빈이 안 됨
              AuditorProvider · GlobalExceptionHandler · InboxProcessor ...
                    │
                    └── 자동 설정의 @Bean 으로 선언
```

> **`@RestControllerAdvice` 는 떼면 안 됩니다.** `GlobalExceptionHandler` ·
> `TraceIdResponseAdvice` 는 **애노테이션으로 찾아지므로** `@Bean` 으로 등록해도
> 클래스의 애노테이션은 유지해야 어드바이스로 동작합니다.

<br><br>

---

### 7-2. 자동 설정을 여섯으로 나눈 이유

```
조건 축이 정확히 이 개수로 갈림

서블릿 웹 + webmvc          Web
서블릿 웹 + security        Security
JPA                        Jpa
JPA + Kafka                Messaging
Kafka                      Kafka
없음                        Async
```

**`config/` 폴더에 모은 이유입니다.**

| | |
|---|---|
| 자동 설정 클래스는 **스캔에 걸리면 안 되는 특수 부류** | 한 폴더에 격리하는 편이 안전 |
| `AutoConfiguration.imports` 와 1:1 로 대조됨 | 빠뜨렸는지 눈으로 보임 |

---

**`@EnableAsync` 만 조건이 없습니다.**

outbox 조건에 묶여 있으면 *"outbox 가 켜질 때만 비동기가 되는"* 결합이 생깁니다.
**verdict 의 policy + pet 병렬 호출처럼 outbox 와 무관한 `@Async` 사용처가
설계에 있습니다.**

> **스레드풀은 자체 `Executor` 빈을 만들지 않습니다.** Boot 의
> `applicationTaskExecutor` + `spring.task.execution.pool.*` 프로퍼티에 맡깁니다.
> 우리가 빈을 정의하면 `TaskExecutionAutoConfiguration` 과 충돌할 여지가 생깁니다.

<br><br>

---

### 7-3. `@Async` 와 `@Transactional` 을 같은 메서드에 안 붙임

```
둘 다 기본 order 가 LOWEST_PRECEDENCE
        │
        └── 프록시 적용 순서가 보장되지 않음
              @Transactional 이 바깥이면 호출 스레드에서 트랜잭션을 열고
              비동기로 넘어가며 끊김
```

**빈을 둘로 쪼갰습니다.**

```
OutboxCommitListener    @Async + @TransactionalEventListener    트랜잭션 없음
        │
        └──▶  OutboxPublisher    @Transactional(REQUIRES_NEW)   발행 + 상태 갱신
```

**부수 효과** — Relay 와 Listener 에 두 벌 복사돼 있던 발행 로직이 한 곳으로 모였습니다.

---

**`publish(UUID)` 가 엔티티가 아니라 ID 를 받는 이유입니다.**

다른 트랜잭션에서 온 엔티티는 **준영속이라 dirty checking 이 안 걸립니다.**
새 트랜잭션이 직접 조회해야 `save()` 없이 UPDATE 가 나갑니다.

<br><br>

---

### 7-4. 모듈을 쪼개지 않은 이유

**`common-web` · `common-jpa` · `common-messaging` 으로 나누면** 조건부 로딩이
거의 불필요해집니다. 그런데 안 했습니다.

| | 쪼개면 | 지금 |
|---|---|---|
| 레포 · publish · 버전 | **3배** | 1개 |
| 조건부 로딩 | 거의 불필요 | 애노테이션 한 줄 |
| 경계를 정하는 근거 | **없음** — `PageResponse` 가 `Page` 를 참조하는데 web 인가 jpa 인가 | — |
| 되돌리기 | 비쌈 | — |

**서비스가 0개였던 시점에 경계를 정할 근거가 없었습니다.**

---

**verdict 가 common 을 아예 안 쓰는 안도 검토했습니다.**

```
verdict 는 common 7개 패키지 중 6개를 씀
        │
        └── entity/ + audit/ 하나만 안 씀
              안 쓰면 6개를 복사해야 하고
              한쪽만 고쳐지면 응답 형태가 조용히 갈림
```

**최후 수단으로 남겨 두었습니다.** 조건부 로딩이 계속 문제를 일으키면 그때 봅니다.

<br><br>

---

### 7-5. 토픽 이름을 여기 두지 않는 이유

```
토픽은 개발 도중 추가·변경·삭제됨
        │
        └── 공통 모듈에 있으면 그때마다
              재배포 + 전 서비스 버전업이 필요함
```

**이 모듈의 기준은 "거의 바뀌지 않는 것" 입니다.**

**대신 문자열이 두 저장소에 존재합니다.** 발행 쪽 `getTopic()` 과 받는 쪽
`@KafkaListener(topics = ...)` 입니다.

> **어긋나면 오류가 나지 않고 이벤트만 오지 않습니다.**
> 완화책은 **`service-template` README 9-2 를 단일 참조로 삼고 Kafka UI 로
> 실물을 확인하는 것**입니다.

**`AuthContextHeaders` 도 같은 부류입니다** — 게이트웨이가 common 을 안 써서
4번째 사본이 됩니다.

<br><br>

---

### 7-6. 발행 시 인증 헤더를 싣지 않음

```
컨슈머가 만드는 행의 created_by
        │
        └── AuditorProvider.current() 가 판정한 값
              각 서비스의 app.auditor.system-name (ingest 면 ingest-batch)
```

| 버림 | 왜 |
|---|---|
| outbox 에 `created_by` 컬럼 추가 | **`OutboxMessage.create()` 를 부르는 서비스가 매번 값을 넘겨야 함.** 빠뜨리면 조용히 null |
| `@PrePersist` 로 자동화 | 엔티티가 스프링 빈을 참조하게 되어 원칙과 어긋남 |

**대가가 작은 근거** — 사용자가 일으키는 이벤트 셋의 소비자가 하는 일이
**재검토 큐 적재 · 캐시 무효화 · 자기 데이터 삭제**라 **새 엔티티를 만드는 자리가
거의 없습니다.**

> **인터셉터는 "헤더 있으면 복원, 없으면 통과"** 라 나중에 발행 쪽을 붙여도
> **인터셉터 코드는 안 바뀝니다.** 고칠 자리도 `OutboxPublisher` 한 곳입니다.

---

**발행 쪽 인터셉터가 애초에 불필요합니다.**

```
Outbox 를 쓰므로 kafkaTemplate.send() 를 부르는 곳이 OutboxPublisher 한 곳뿐
        │
        └── 거기는 @Async · 스케줄러 스레드라 SecurityContext 가 이미 비어 있음
              꺼낼 값이 없음
```

<br><br>

---

### 7-7. UUID v7 생성기가 두 벌인 이유

```
PK          Hibernate @UuidGenerator(style = VERSION_7)
이벤트 ID    EventEnvelope.generateUuidV7()   자체 구현
```

**Hibernate 것을 재사용하면 안 됩니다.**

```
UuidVersion7Strategy 는 hibernate-core 에 있음
        │
        └── 무상태 서비스(verdict · congestion)에는 Hibernate 가 없음
              그런데 verdict 도 이벤트를 소비하며 EventEnvelope 를 다룸
                    │
                    └── 재사용하면 message/ 가 JPA 에 묶여
                        조건부 로딩의 의미가 사라짐
```

<br><br>

---

### 7-8. `X-Original-Trace-Id` 를 손으로 안 넣음

**스프링 카프카 Observation 에 맡깁니다.**

```
우리가 만든 헤더    우리가 넣음      X-User-Id · X-User-Role
W3C 표준 헤더      라이브러리가     traceparent
```

**손으로 넣으면 헤더가 중복되어 표준상 무효 처리되고 받는 쪽이 새 trace 를
시작합니다.**

> ⚠ **단 Kafka 는 Observation 을 켜야만 동작합니다.**
>
> ```yaml
> spring:
>   kafka:
>     template:  { observation-enabled: true }
>     listener:  { observation-enabled: true }
> ```
>
> **프로듀서만 켜면 헤더는 실려 가지만 컨슈머가 읽지 않아 반쪽입니다.**

<br><br>

---

## 8. 현재 상태

<br><br>

---

### 8-1. 버전 이력

| 버전 | 무엇이 | 왜 |
|---|---|---|
| `0.0.1` ~ `0.0.4` | 초기 구축 | 1~7단계 |
| `0.0.5` | `findUnpublishedIgnoringRetryLimit` 추가 | Relay 가 포기한 건을 관리자가 볼 수단이 없었음 |
| `0.0.6` | **QueryDSL 프로세서** | `BaseEntity` 를 상속하면 `QBaseEntity` 가 없어 컴파일 실패 |
| `0.0.7` | `RESOURCE_NOT_FOUND` · `AuthenticationManager` | 없는 경로가 500 · 로그에 비밀번호가 찍힘 |
| `0.0.8` | **`FOR UPDATE SKIP LOCKED`** | 같은 이벤트가 두 번 발행됨 |
| `0.0.9` | `findGivenUpMessages` 로 교체 | 주석과 쿼리가 어긋나 있었음 |

---

**`0.0.6` 이 가장 넓게 영향이 있었습니다.**

```
BaseEntity 를 @MappedSuperclass 로 제공하면서 그 Q 클래스를 안 만들어 준 것
        │
        └── 도메인 서비스 13개가 전부 같은 자리를 밟음
              service-template 은 BaseEntity 를 상속하는 실물이 없어
              지금까지 안 드러났고 auth 가 첫 사례였음
```

---

**`0.0.9` 가 `feat` 이 아니라 `fix` 인 이유입니다.**

`0.0.5` 의 주석은 이미 *"관리자는 임계를 넘긴 건을 봐야 함"* 이라고 말하는데
**쿼리에는 그 조건이 없어 미발행 전부를 돌려주고 있었습니다.**

<br><br>

---

### 8-2. 실물로 검증된 것

```
✅ GlobalExceptionHandler 5개          service-template 임시 컨트롤러로 5종
✅ TraceIdResponseAdvice               실패 응답에도 traceId 가 실림
✅ jsonb + Jackson 3                   auth 첫 회원가입
✅ DomainEvent 의 @JsonIgnore 상속       payload 에 라우팅 필드 3개가 안 샘
✅ Outbox → Kafka                      auth account.created
✅ FOR UPDATE SKIP LOCKED              Kafka 를 내려 겹치는 상황을 만들어 확인
✅ OutboxRelay 안전망                   리스너가 실패한 건을 주워 발행
✅ 관리자 경로가 ADMIN 으로만 통과        auth #17 검증
✅ jar 내용                            imports · 자동 설정 6개 · V1 · V2 · QBaseEntity
```

<br><br>

---

### 8-3. 아직 확인 못 한 것

**소비자가 있는 서비스가 생겨야 확인됩니다.**

```
⬜ Inbox 전체 흐름              InboxProcessor 의 트랜잭션이 실제로 묶이나
⬜ KafkaSecurityInterceptor    리스너 컨테이너에 실제로 붙나
⬜ 재시도 · DLQ                1s → 2s → 4s 뒤 {토픽}.dlq 로 가나
⬜ RecordMessageConverter      EventEnvelope<T> 로 받아지나
```

> **auth 로는 확인할 수 없습니다.** `@KafkaListener` 가 한 개도 없어
> 인터셉터가 붙을 자리 자체가 없습니다.
>
> **user · pet · search · review · notification 중 하나가 생기면 확인됩니다.**

<br><br>

---

### 8-4. 알려진 결함

**`HttpMessageNotReadableException` 핸들러가 없습니다.**

```
깨진 JSON 을 보내면
        │
        └── Exception 폴백이 잡아 INTERNAL_ERROR 500
              본문 형식이 틀린 것은 클라이언트 잘못이므로 400 이 맞음
              지금은 서버 오류로 보고되어 로그·모니터링에서 진짜 장애와 섞임
```

**재현**

```bash
curl -X POST http://localhost:8081/api/v1/auth/login \
  -H "Content-Type: application/json" -d '{"email"'
```

`CommonErrorCode.VALIDATION_FAILED` 를 그대로 쓰면 됩니다.
**다음에 이 모듈을 손볼 때 함께 처리합니다.**

---

**`HttpRequestMethodNotSupportedException` 도 같습니다.** 405 인데 500 으로 나갑니다.

<br><br>

---

### 8-5. 아직 안 정한 것

| 무엇 | 언제 |
|---|---|
| **`RestClientAuthInterceptor` 배선** | **verdict · search 착수 시** — auth 는 `/internal` 호출이 0건이라 정할 수 없음 |
| `CommonApiResponse` 역직렬화 | 같은 시점. 생성자가 private 이고 `@JsonCreator` 가 없어 읽는 쪽이 걸림 |
| 소프트 딜리트 조회 방식 | 실제 조회를 짜는 서비스에서 |
| `processed_event` 정리 배치 | 지금 규모에서는 불필요 |
| `max.block.ms` 를 낮출지 | 카프카가 죽었을 때 3초 타임아웃이 60초에 가려짐 |
| 조직 전용 BOM | 지금은 Boot 버전이 21개 레포에 사본으로 있음 |

---

**`RestClientAuthInterceptor` 가 지금 아무 일도 안 합니다.**

```
클래스는 작성·커밋돼 있으나 어떤 RestClient.Builder 에도 연결돼 있지 않음
        │
        └── 맨 ClientHttpRequestInterceptor 빈은 자동으로 적용되지 않음
              반드시 빌더에 붙여야 하는데 후보 경로가 셋이고
              서비스가 빌더를 어떻게 선언하느냐로 갈림
```

| 후보 | 걸리는 것 |
|---|---|
| `RestClientCustomizer` 빈 | Boot 이 자동 설정한 빌더에만 적용됨 |
| `@LoadBalanced RestClient.Builder` | 서비스가 `RestClient.builder()` 로 새로 만들면 안 걸림 |
| Boot 4 의 `@HttpExchange` 자동 설정 | 자체 group configurer 메커니즘이 따로 있음 |

**확인 방법** — 서비스 A 가 인증된 요청 처리 중에 B 를 호출했을 때
**B 가 만든 행의 `created_by` 가 `SYSTEM` 이 아니라 실제 accountId** 여야 합니다.

> **에러가 안 나므로 이것을 안 보면 모릅니다.**

<br><br>

---

## 9. 용어

공통 용어는 `service-template` README 11장에 있습니다. **여기는 이 모듈에서만 쓰는 말**입니다.

| 용어 | 뜻 |
|---|---|
| **라이브러리** | 혼자 실행되지 않고 다른 앱에 끼워지는 코드 묶음. 이 저장소 |
| **jar** | 자바 코드를 압축한 파일. 실행 가능한 것과 끼워 넣는 것이 있음 |
| **자동 설정** | 의존성이 있으면 스프링이 알아서 Bean 을 올리는 것. `@AutoConfiguration` |
| **클래스패스** | 그 서비스가 빌드에 넣은 라이브러리 전부. 자동 설정의 판단 기준 |
| **Bean** | 스프링이 만들어 관리하는 객체. `@Bean` · `@Component` |
| **`@ConditionalOnClass`** | 이 클래스가 클래스패스에 있을 때만 켜라 |
| **`@ConditionalOnMissingBean`** | 같은 타입의 Bean 이 없을 때만 만들라 — 서비스가 정의하면 물러남 |
| **트랜잭션** | 여러 쓰기를 한 덩어리로. 전부 되거나 전부 안 되거나 |
| **`MANDATORY`** | 트랜잭션이 이미 있어야만 실행. 없으면 즉시 예외 |
| **`REQUIRES_NEW`** | 기존 트랜잭션과 별개로 새 트랜잭션을 열고 먼저 커밋 |
| **이벤트** | "무슨 일이 있었다" 를 Kafka 에 남기는 것. 직접 호출의 대안 |
| **토픽** | Kafka 의 우체통 하나. `place.updated` |
| **봉투 (`EventEnvelope`)** | 모든 이벤트를 감싸는 공통 껍데기. `eventId` · `data` … |
| **Outbox** | 이벤트를 DB 에 먼저 저장하고 커밋 뒤 보내는 패턴 |
| **Inbox** | 같은 이벤트를 두 번 처리하지 않게 처리 이력을 남기는 패턴 |
| **Relay** | outbox 표에서 안 나간 건을 주기적으로 주워 보내는 안전망 |
| **DLQ** | 재시도가 끝난 메시지가 가는 토픽 |
| **멱등** | 여러 번 처리해도 결과가 한 번과 같음 |
| **`FOR UPDATE SKIP LOCKED`** | 행을 잠그되 이미 잠긴 행은 건너뜀. 중복 발행 방지 |
| **`SecurityContext`** | 이 요청의 사용자 정보를 담아 두는 자리 |
| **principal** | 인증된 사용자. 우리는 `CustomUserPrincipal(accountId, role)` |
| **감사 (Auditing)** | 누가 언제 만들고 고쳤는지 자동으로 기록. `BaseEntity` 6컬럼 |
| **소프트 딜리트** | 행을 지우지 않고 `deleted_at` 만 채움 |
| **Flyway** | DB 스키마를 번호 붙인 SQL 로 관리. `V1__outbox.sql` |
| **publish** | 이 모듈을 GitHub Packages 에 올리는 것. 버전 하나를 영구 소모 |
| **`mavenLocal()`** | 내 컴퓨터의 임시 저장소. publish 전 검증용 |
