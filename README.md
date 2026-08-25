# 함께하개 서비스 템플릿

이 문서는 서비스 레포를 새로 만든 뒤 무엇을 어떻게 세팅하고, 각 폴더와 파일이 무슨 일을 하는지를 정리한 개발 지침입니다.

**개발하는 동안 계속 참고합니다.** 구현이 끝나면 이 내용을 지우고 해당 서비스를 설명하는 README로 교체합니다.

파일 이름 뒤의 괄호는 자바 타입을 뜻합니다. 자바에서는 클래스, 인터페이스, enum, record가 모두 `.java` 파일이라 이름만으로는 구분되지 않으므로 따로 표기했습니다.

## 기술 스택

| 항목 | 버전 |
|---|---|
| Java | 21 |
| Spring Boot | 4.1.1 |
| Spring Cloud | 2025.1.3 (Oakwood) |
| Gradle | 9.5.1 (래퍼로 고정) |
| PostgreSQL | 17 + PostGIS 3.5 |
| QueryDSL | io.github.openfeign.querydsl 7.6 |
| springdoc-openapi | 3.1.0 |

Spring Boot 3.x 계열은 2026년 6월 30일에 오픈소스 지원이 끝나 사용하지 않습니다.

QueryDSL은 본가(`com.querydsl`)가 아니라 OpenFeign 포크를 사용합니다. **groupId만 다르고 패키지명은 `com.querydsl` 그대로**이므로 소스 코드는 일반적인 QueryDSL 예제와 동일하게 작성합니다.

---

## 1. 복제 후 최초 설정

TEMPLATE_REPO에서 "Use this template"으로 새 레포를 만든 뒤 한 번만 수행하는 절차입니다.

### 1-1. 패키지 경로 치환은 IntelliJ를 열기 전에

템플릿의 패키지 경로는 `com.pawtrail.template`입니다. 이것을 서비스명으로 바꿔야 하는데, IntelliJ로 프로젝트를 연 뒤에 바꾸면 프로젝트 구조 정보가 이전 이름으로 남아 모듈을 다시 인식시키는 작업이 따라붙습니다. **clone 직후 터미널에서 먼저 처리한 다음 IntelliJ를 여는 것이 가장 빠릅니다.**

아래는 place 서비스를 만드는 예시입니다. 맨 위 값 두 개만 자기 도메인에 맞게 바꾸고, **레포 루트에서** 나머지를 그대로 실행합니다.

운영체제마다 명령어가 다르므로 해당하는 것 하나만 실행합니다.

#### macOS

터미널에서 실행합니다. macOS에 기본 설치된 sed는 `-i` 뒤에 빈 인자를 하나 더 요구하므로 `sed -i ''` 형태입니다.

```bash
NEW=place        # 소문자 서비스명
CLASS=Place      # 첫 글자만 대문자로 바꾼 것

# 1) 패키지 폴더 이름 변경 (main, test 양쪽)
mv src/main/java/com/pawtrail/template src/main/java/com/pawtrail/$NEW
mv src/test/java/com/pawtrail/template src/test/java/com/pawtrail/$NEW

# 2) 소스 안의 package 선언과 import 치환
grep -rl "com.pawtrail.template" src | xargs sed -i '' "s/com\.pawtrail\.template/com.pawtrail.$NEW/g"

# 3) TemplateApplication.java 를 <서비스명>Application.java 로 변경
mv src/main/java/com/pawtrail/$NEW/TemplateApplication.java \
   src/main/java/com/pawtrail/$NEW/${CLASS}Application.java
grep -rl "TemplateApplication" src | xargs sed -i '' "s/TemplateApplication/${CLASS}Application/g"
```

#### Windows — Git Bash

Git for Windows를 설치하면 함께 들어오는 Git Bash에서 실행합니다. **Windows에서는 이 방법을 권장합니다.** 명령어는 macOS와 같고 `sed -i` 뒤에 빈 인자만 없습니다.

```bash
NEW=place        # 소문자 서비스명
CLASS=Place      # 첫 글자만 대문자로 바꾼 것

# 1) 패키지 폴더 이름 변경 (main, test 양쪽)
mv src/main/java/com/pawtrail/template src/main/java/com/pawtrail/$NEW
mv src/test/java/com/pawtrail/template src/test/java/com/pawtrail/$NEW

# 2) 소스 안의 package 선언과 import 치환
grep -rl "com.pawtrail.template" src | xargs sed -i "s/com\.pawtrail\.template/com.pawtrail.$NEW/g"

# 3) TemplateApplication.java 를 <서비스명>Application.java 로 변경
mv src/main/java/com/pawtrail/$NEW/TemplateApplication.java \
   src/main/java/com/pawtrail/$NEW/${CLASS}Application.java
grep -rl "TemplateApplication" src | xargs sed -i "s/TemplateApplication/${CLASS}Application/g"
```

#### Windows — PowerShell

Git Bash를 쓸 수 없을 때만 사용합니다. 파일을 다시 쓰는 과정에서 인코딩이 바뀌면 한글 주석이 깨질 수 있으므로 **PowerShell 7 이상**에서 실행합니다.

```powershell
$NEW   = "place"    # 소문자 서비스명
$CLASS = "Place"    # 첫 글자만 대문자로 바꾼 것

# 1) 패키지 폴더 이름 변경 (main, test 양쪽)
Rename-Item "src\main\java\com\pawtrail\template" $NEW
Rename-Item "src\test\java\com\pawtrail\template" $NEW

# 2) 소스 안의 package 선언과 import 치환
Get-ChildItem -Path src -Recurse -File | ForEach-Object {
    (Get-Content $_.FullName -Raw) `
        -replace "com\.pawtrail\.template", "com.pawtrail.$NEW" `
        -replace "TemplateApplication", "${CLASS}Application" |
        Set-Content $_.FullName -NoNewline -Encoding utf8
}

# 3) TemplateApplication.java 를 <서비스명>Application.java 로 변경
Rename-Item "src\main\java\com\pawtrail\$NEW\TemplateApplication.java" "${CLASS}Application.java"
```

#### 제대로 바뀌었는지 확인

아래 명령이 **아무것도 출력하지 않으면** 성공입니다. 무언가 출력된다면 그 파일에 옛 이름이 남아 있는 것이므로 직접 고칩니다.

```bash
# macOS / Git Bash
grep -r "com.pawtrail.template\|TemplateApplication" src
```

```powershell
# PowerShell
Select-String -Path src\*.* -Pattern "com\.pawtrail\.template|TemplateApplication" -Recurse
```

### 1-2. 이미 IntelliJ로 연 뒤에 바꿨다면

프로젝트 정보가 이전 이름으로 캐시되어 있어 모듈을 제대로 인식하지 못합니다. IntelliJ를 닫고 프로젝트 루트의 `.idea` 폴더와 `*.iml` 파일을 지운 다음 다시 열면 처음부터 다시 인식합니다. 소스 코드에는 영향이 없습니다.

### 1-3. 직접 고쳐야 하는 파일

치환 스크립트로 처리되지 않는 파일들입니다. 아래 예시는 모두 place 서비스를 만드는 경우이며, `place` 자리에 자기 도메인명을 넣습니다.

#### settings.gradle

프로젝트 이름을 바꿉니다. 이 값이 빌드 산출물 jar 이름이 되므로 Dockerfile과도 연결됩니다.

```groovy
// 바꾸기 전
rootProject.name = 'template'

// 바꾸기 후
rootProject.name = 'place'
```

#### gradle.properties

공통 모듈 버전을 최신으로 맞춥니다. 최신 버전은 조직 Packages 페이지에서 확인합니다.

```properties
# 바꾸기 전
commonVersion=0.1.0

# 바꾸기 후 (예시)
commonVersion=0.2.3
```

#### src/main/resources/application.yml

서비스 이름, 포트, DB 접속 정보를 채웁니다. 포트는 다른 서비스와 겹치지 않아야 하며, 서비스별 배정 포트는 infra 레포에 정리되어 있습니다.

```yaml
# 바꾸기 전
spring:
  application:
    name: template-service
  datasource:
    url: jdbc:postgresql://<DB주소>:5432/template_db
    username: template_user
    password: ${DB_PASSWORD}
  jpa:
    hibernate:
      ddl-auto: validate
server:
  port: 8080
app:
  auditor:
    system-name: SYSTEM
  outbox:
    relay:
      enabled: false
```

```yaml
# 바꾸기 후
spring:
  application:
    name: place-service
  datasource:
    url: jdbc:postgresql://<DB주소>:5432/place_db
    username: place_user
    password: ${DB_PASSWORD}
  jpa:
    hibernate:
      ddl-auto: validate
server:
  port: 8081
app:
  auditor:
    system-name: SYSTEM
  outbox:
    relay:
      enabled: true
```

`app.outbox.relay.enabled`는 이벤트를 발행하는 서비스에서만 `true`로 둡니다. 인스턴스를 여러 개 띄우는 서비스라면 한 인스턴스에서만 켭니다. `app.auditor.system-name`은 배치가 아닌 서비스에서는 `SYSTEM` 그대로 둡니다.

#### Dockerfile

jar 경로가 `settings.gradle`의 이름을 따라갑니다. 아래처럼 와일드카드로 되어 있다면 고치지 않아도 됩니다.

```dockerfile
# 이 형태라면 그대로 둡니다
COPY build/libs/*.jar app.jar

# 이름이 박혀 있다면 바꿉니다
# 바꾸기 전
COPY build/libs/template-0.0.1-SNAPSHOT.jar app.jar
# 바꾸기 후
COPY build/libs/place-0.0.1-SNAPSHOT.jar app.jar
```

#### Jenkinsfile

파이프라인 본체는 공유 라이브러리에 있으므로 파라미터 3개만 채웁니다. `deployNode`는 이 서비스가 올라갈 노드이고, `instances`는 띄울 개수입니다. 어느 노드에 몇 개인지는 9장의 분류표와 인프라 문서를 따릅니다.

```groovy
// 바꾸기 전
@Library('pawtrail-pipeline') _
springServicePipeline(
    serviceName: 'template',
    deployNode : 'app',
    instances  : 1
)

// 바꾸기 후
@Library('pawtrail-pipeline') _
springServicePipeline(
    serviceName: 'place',
    deployNode : 'core',
    instances  : 1
)
```

#### src/main/resources/db/migration/

템플릿에 들어 있는 예시 스크립트를 지우고, 이 서비스의 첫 스크립트를 만듭니다. 파일명은 `V20__<서비스명>.sql`입니다.

```
바꾸기 전   db/migration/V20__template.sql   (예시 내용)
바꾸기 후   db/migration/V20__place.sql      (이 서비스의 테이블 정의)
```

```sql
-- V20__place.sql 예시
CREATE TABLE place (
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(200) NOT NULL,
    -- ... 이 서비스의 컬럼 정의
    created_at      TIMESTAMP    NOT NULL,
    created_by      VARCHAR(45)  NOT NULL,
    updated_at      TIMESTAMP,
    updated_by      VARCHAR(45),
    deleted_at      TIMESTAMP,
    deleted_by      VARCHAR(45)
);
```

마지막 6개 컬럼은 모든 테이블이 공통으로 갖습니다. `BaseEntity`가 이 컬럼들과 짝을 이루므로 빠뜨리면 기동 시 검증에 실패합니다.

#### README.md

**지금은 고치지 않습니다.** 이 파일에는 지금 읽고 있는 개발 지침이 들어 있으며, 개발하는 동안 계속 참고하게 됩니다.

구현이 끝난 뒤에 이 지침 내용을 전부 지우고, 해당 서비스에 맞는 README로 새로 작성합니다. 참고용 최종 형태는 다음과 같습니다.

```markdown
# place

## 역할
장소 마스터 데이터를 소유하고 조회를 제공합니다.

## 소유 데이터
place_db — place, place_source_link, place_facility

## 의존
- 호출하는 서비스: ingest (/internal/raw)
- 구독하는 이벤트: place.ingested
- 발행하는 이벤트: place.updated

## 로컬 실행
docker compose --profile infra --profile edge up -d
이후 PlaceApplication 을 IntelliJ에서 실행합니다.
```

의존 관계 항목은 나중에 서비스 간 호출 관계를 파악하는 근거가 되므로 반드시 채웁니다. 서비스가 여러 개이므로 이 항목만 모아 읽으면 전체 호출 관계가 드러납니다.

### 1-4. DB를 사용하지 않는 서비스라면

verdict, congestion처럼 DB가 없는 서비스는 `<서비스명>Application.java`(예: `VerdictApplication.java`)를 열어 아래 두 줄을 지웁니다. 남겨두면 JPA가 필수가 되어 기동에 실패합니다.

```java
@EntityScan(basePackages = { ... })
@EnableJpaRepositories(basePackages = { ... })
```

함께 `src/main/resources/db/migration/` 폴더와 `infrastructure/persistence/` 패키지도 지웁니다.

### 1-5. 공통 모듈의 자동 설정 파일은 건드리지 않습니다

공통 모듈에는 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 파일이 있어 설정 클래스들이 자동으로 등록됩니다. 이 파일은 **공통 모듈 레포에만 존재하며, 서비스 레포에서는 만들지도 고치지도 않습니다.**

서비스 쪽에서 필요한 것은 `<서비스명>Application.java`의 `scanBasePackages`에 `com.pawtrail.common`을 함께 지정하는 것뿐이고, 이는 템플릿에 이미 들어 있습니다. 여기에 같은 이름의 파일을 새로 만들면 설정이 두 번 등록되어 빈 충돌이 발생할 수 있습니다.

### 1-6. 마지막으로 빌드가 되는지 확인합니다

```bash
# macOS / Git Bash
./gradlew compileJava

# Windows PowerShell — 앞의 .\ 를 빠뜨리면 명령을 찾지 못합니다
.\gradlew.bat compileJava
```

**`BUILD SUCCESSFUL` 만 보고 넘어가지 않습니다.** 그 아래 `actionable tasks: N executed` 줄을 함께 확인합니다. 소스가 컴파일 대상에 잡히지 않으면 Gradle은 아무 일도 하지 않고 성공으로 끝나기 때문입니다. 패키지 경로 치환이 잘못되었을 때 이 형태로 나타납니다.

#### 터미널에서 실행할 때 JAVA_HOME 주의

**터미널의 `gradlew` 는 IntelliJ 설정의 Gradle JVM 을 보지 않고 `JAVA_HOME` 환경변수를 봅니다.** 둘은 별개이므로, IntelliJ 설정이 21로 되어 있어도 터미널에서는 다음과 같은 오류가 날 수 있습니다.

```
Gradle requires JVM 17 or later to run. Your build is currently configured to use JVM 11.
```

이때는 `JAVA_HOME` 을 JDK 21 경로로 맞춥니다. 설치 경로는 JDK 배포판마다 다르므로 직접 확인합니다.

```powershell
# 설치된 21 찾기
Get-ChildItem "C:\Program Files\*\*" -Directory | Where-Object { $_.Name -like "*21*" } | Select-Object FullName

# 이 터미널에서만 지정 (시스템 설정을 건드리지 않습니다)
$env:JAVA_HOME = "C:\Program Files\Java\jdk-21"
```

시스템 환경변수를 직접 바꿨다면 **터미널 창을 새로 열어야** 반영됩니다.

### 1-7. IntelliJ 에 표시되는 경고 두 가지는 정상입니다

세팅을 마쳐도 IntelliJ 가 아래 두 가지를 빨간 줄로 표시합니다. 컴파일과 기동에는 영향이 없습니다.

| 표시되는 곳 | 이유 |
|---|---|
| `TemplateApplication.java` 의 `com.pawtrail.common` | 공통 모듈이 아직 의존성에 없어서입니다. 3장에 따라 공통 모듈을 연결하면 사라집니다 |
| `application.yml` 의 `app.auditor.system-name`, `app.outbox.relay.enabled` | 이 프로젝트가 직접 정의한 프로퍼티라 스프링이 아는 목록에 없어서입니다. 공통 모듈에 설정 클래스가 추가되면 사라집니다 |

`scanBasePackages` 와 `@EntityScan` 은 문자열을 받으므로, 해당 패키지가 실제로 없어도 컴파일과 기동이 정상적으로 이루어집니다.

---

## 2. 로컬 실행 환경

### 2-1. 무엇이 어디서 도는가

로컬에서는 인프라와 플랫폼을 Docker Compose로 띄우고, **지금 작업 중인 도메인 서비스만 IntelliJ에서 직접 실행합니다.** 도메인 서비스를 전부 컨테이너로 올리면 메모리 부족이 발생 할 수 있고, 코드를 고칠 때마다 이미지를 다시 만들어야 해 개발 속도가 크게 떨어집니다.

| 어디서 도는가 | 무엇이 |
|---|---|
| AWS EC2 (팀 공용) | PostgreSQL 하나 |
| 내 PC · Docker Compose | Redis, Kafka, 관측 스택, nginx, gateway, config, eureka |
| 내 PC · IntelliJ | 지금 작업 중인 서비스 1~3개 |

PostgreSQL만 공용으로 두는 이유는 수집한 데이터를 함께 쓰기 위해서입니다. 수집 API에 하루 호출 제한이 있어 데이터를 채우는 데 여러 날이 걸리고, 추출 배치는 GPU가 필요해 각자 재현할 수 없습니다.

Kafka를 각자 로컬에 두는 이유는 반대입니다. 공용으로 쓰면 한 사람이 발행한 이벤트를 다른 사람의 컨슈머가 가져가 버려 서로의 테스트가 섞입니다.

### 2-2. Compose 프로파일

| 프로파일 | 포함 | 언제 켜는가 |
|---|---|---|
| `infra` | Redis, Kafka | 거의 항상 |
| `edge` | nginx, gateway, config, eureka | 게이트웨이를 거친 호출이나 인증을 확인할 때 |
| `observability` | Prometheus, Grafana, Loki, Zipkin | 추적이나 메트릭을 볼 때 |
| `tools` | Kafdrop | 토픽에 메시지가 실제로 실렸는지 확인할 때 |
| `pipeline` | ingest, extract | 수집·추출 배치를 돌릴 때만 |
| `app` | 도메인 서비스 전체 | 배포 검증 때만 |

```bash
# 가장 흔한 조합
docker compose --profile infra --profile edge up -d

# 추적까지 보고 싶을 때
docker compose --profile infra --profile edge --profile observability up -d

# 내리기
docker compose --profile infra --profile edge down
```

`infra` 와 `edge` 를 띄운 뒤 자기 서비스를 IntelliJ 에서 실행하면, 서비스가 유레카에 등록되고 게이트웨이를 통해 호출할 수 있게 됩니다. 다른 서비스를 호출해야 한다면 그 서비스도 IntelliJ 에서 함께 띄웁니다.

Compose 파일은 이 레포가 아니라 infra 레포에 있습니다. Redis 와 Kafka 는 사람당 하나만 떠 있어야 하므로 서비스 레포마다 두지 않습니다.

### 2-3. 포트 배정

포트가 겹치면 뒤에 뜬 서비스가 기동에 실패합니다. 아래 배정을 따릅니다.

**플랫폼**

| | 포트 |
|---|---|
| nginx | 80 |
| gateway | 8080 |
| eureka | 8761 |
| config | 8888 |
| 프론트엔드 (Vite) | 5173 |

**도메인 서비스**

| 서비스 | 포트 | | 서비스 | 포트 |
|---|---|---|---|---|
| auth | 8081 | | extract | 8089 |
| user | 8082 | | congestion | 8090 |
| pet | 8083 | | route | 8091 |
| place | 8084 | | report | 8092 |
| policy | 8085 | | notification | 8093 |
| verdict | 8086 | | | |
| search | 8087 | | | |
| ingest | 8088 | | | |

**인프라**

Redis 6379 / Kafka 9092 / Kafdrop 9000 / Prometheus 9090 / Grafana 3000 / Loki 3100 / Zipkin 9411 / PostgreSQL 5432(원격)

프론트엔드를 3000 이 아니라 5173 에 두는 이유는 Grafana 가 3000 을 쓰기 때문입니다.

배포 환경에서 인스턴스를 여러 개 띄우는 서비스는 **기본 포트 + 100 단위**로 배정합니다. verdict 는 8086·8186·8286, search 는 8087·8187, gateway 는 8080·8180 입니다. 로컬에서는 인스턴스가 하나씩이므로 기본 포트만 사용합니다.

### 2-4. 메모리 주의

메모리 16GB를 기준으로 컨테이너마다 메모리 상한을 걸어두었고, JVM을 쓰는 컨테이너에는 힙 상한을 함께 지정했습니다.

**힙 상한을 지정하지 않으면 컨테이너가 아무 로그도 남기지 않고 종료됩니다.** JVM은 컨테이너에 걸린 상한과 무관하게 물리 메모리의 일정 비율까지 힙을 늘리려 하기 때문에, 컨테이너 상한을 넘는 순간 강제 종료됩니다. 원인을 추적하기 어려운 형태로 죽으므로 `-XX:MaxRAMPercentage`로 컨테이너 상한 대비 비율을 지정합니다.

Apple Silicon 맥에서는 arm64 이미지가 있는지 확인합니다. Kafka는 arm64를 지원하는 공식 이미지를 사용합니다.

### 2-5. 공용 DB에 연결하기

PostgreSQL 인스턴스는 하나지만 그 안에 서비스별 DB와 전용 계정이 나뉘어 있습니다. 각 계정은 **자기 DB에만 접속할 수 있습니다.** 다른 서비스의 DB에 붙으려 하면 거부되는데, 이는 설정 오류가 아니라 의도된 격리입니다.

접속 주소와 비밀번호는 팀 내부에서 전달받아 `.env` 에 넣습니다. `application.yml` 에는 값을 직접 적지 않고 환경변수를 참조합니다.

```yaml
spring:
  datasource:
    url: jdbc:postgresql://${DB_HOST}:5432/place_db
    username: place_user
    password: ${DB_PASSWORD}
  jpa:
    hibernate:
      ddl-auto: validate
```

```
# .env
DB_HOST=<전달받은 주소>
DB_PASSWORD=<전달받은 비밀번호>
```

주소를 환경변수로 두는 이유는 EC2 를 재생성하면 주소가 바뀌기 때문입니다. `.env` 한 줄만 고치면 되고 레포를 손댈 일이 없습니다.

접속하려면 **본인의 공인 IP 가 보안그룹에 등록되어 있어야 합니다.** 인터넷 회선이 바뀌거나 공인 IP 가 갱신되면 다시 등록해야 하므로, 접속이 갑자기 안 될 때 이 부분을 먼저 확인합니다.

`ddl-auto`는 반드시 `validate`로 둡니다. `update`나 `create`로 두면 애플리케이션이 공용 DB의 스키마를 마음대로 바꾸거나 데이터를 지웁니다. 수집에 여러 날이 걸린 데이터가 사라질 수 있습니다.

### 2-6. Flyway는 공용 DB를 바꿉니다

스키마는 Flyway 스크립트로 관리합니다. 그런데 DB가 공용이므로, **내가 실행한 마이그레이션이 그대로 팀원 환경에도 반영됩니다.**

- 새 마이그레이션 스크립트를 추가했다면 팀원에게 알립니다
- 이미 적용된 스크립트 파일은 고치지 않습니다. 내용이 바뀌면 체크섬이 달라져 다음 기동이 실패합니다. 고쳐야 한다면 새 번호로 스크립트를 하나 더 만듭니다
- 서비스별 스크립트는 `V20`부터 시작합니다. `V1`부터 `V19`는 공통 모듈이 사용합니다

---

## 3. 공통 모듈 가져오기와 버전 올리기

공통 모듈은 GitHub Packages에 jar로 배포되어 있습니다. 각 서비스는 이를 의존성으로 당겨 씁니다.

### 3-1. 인증 환경변수 설정

GitHub Packages는 공개 저장소라도 내려받을 때 인증을 요구합니다. 따라서 **빌드하기 전에 환경변수 두 개를 설정해야 합니다.** 설정하지 않으면 빌드가 401로 실패하는데, 오류 메시지에 원인이 드러나지 않아 찾는 데 시간이 걸립니다.

| 환경변수 | 값 |
|---|---|
| `GPR_USER` | 본인의 GitHub 사용자명 |
| `GPR_TOKEN` | GitHub Personal Access Token |

토큰 권한은 내려받기만 한다면 `read:packages` 하나면 충분합니다. 공통 모듈을 배포하는 담당자만 `write:packages`를 추가로 부여받습니다.

토큰은 절대 `build.gradle` 이나 레포 안 파일에 직접 적지 않습니다. 커밋에 함께 올라갑니다.

**공통 모듈이 아직 배포되지 않았다면** `build.gradle` 의 공통 모듈 의존성 줄이 주석 처리된 상태입니다. 이 경우 환경변수가 없어도 빌드가 통과합니다. 공통 모듈이 배포된 뒤에 주석을 풀고 `gradle.properties` 의 `commonVersion` 을 맞춥니다.

### 3-2. build.gradle에서 고치는 부분

버전은 `gradle.properties`에 한 줄로 두고, `build.gradle`이 그 값을 참조합니다.

```properties
# gradle.properties
commonVersion=0.1.0

# 라이브러리 버전도 같은 곳에서 관리합니다
querydslVersion=7.6
springdocVersion=3.1.0
```

```groovy
// build.gradle
repositories {
    mavenCentral()
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
}
```

버전을 `build.gradle`에 직접 적지 않고 `gradle.properties`로 뺀 이유는, 고쳐야 할 자리를 파일 하나로 고정하기 위해서입니다. 레포가 여러 개이므로 버전을 올릴 때 어디를 봐야 하는지가 매번 같아야 합니다.

### 3-3. 공통 모듈 버전을 올리는 절차

1. 공통 모듈 레포에서 `version`을 올리고 GitHub Packages에 배포합니다
2. 이 서비스 레포의 `gradle.properties`에서 `commonVersion` 값을 새 버전으로 고칩니다
3. Gradle을 새로 고칩니다

공통 모듈은 릴리스 버전으로 고정해 사용합니다. 즉 버전을 올리지 않으면 계속 예전 버전으로 빌드됩니다. 컴파일은 정상적으로 되기 때문에 알아채기 어려우므로, **공통 모듈이 변경되었다는 공지를 받으면 이 값부터 확인합니다.**

---

## 4. 설정값을 어디에 두는가

설정값은 성격에 따라 세 곳으로 나뉩니다. 셋은 읽히는 시점이 다르므로 섞어 쓰지 않습니다.

| 두는 곳 | 언제 읽히는가 | 무엇을 두는가 | 커밋 |
|---|---|---|---|
| 레포 안 `gradle.properties` | 빌드할 때 (Gradle) | 빌드에만 쓰이는 값 | 함 |
| 환경변수 (`.env` 또는 IntelliJ 실행 구성) | 빌드·기동 시점 | 비밀값, 사람이나 서버마다 다른 값 | 안 함 |
| 레포 안 `src/main/resources/application.yml` | 애플리케이션 기동 시 | 이 서비스 고유의 값, 동작 스위치 | 함 |
| Config 저장소의 `<서비스명>.yml` | 애플리케이션 기동·갱신 시 | 여러 서비스가 공유하거나 재배포 없이 바꿔야 하는 값 | 함 (비밀값 제외) |

비밀값은 `application.yml`에 직접 쓰지 않고 환경변수를 참조하는 형태로만 적습니다.

```yaml
spring:
  datasource:
    password: ${DB_PASSWORD}
```

### 4-1. 환경변수를 어디에 넣는가

환경변수는 넣는 위치가 실행 방법에 따라 다릅니다. 여기서 자주 막히므로 세 경우를 구분합니다.

| 실행 방법 | 어디에 넣는가 |
|---|---|
| Docker Compose로 컨테이너를 띄울 때 | 프로젝트 루트의 `.env` 파일. Compose가 자동으로 읽습니다 |
| IntelliJ에서 서비스를 직접 실행할 때 | 실행 구성(Run/Debug Configurations)의 Environment variables 칸 |
| Gradle 빌드(공통 모듈 내려받기) | OS 환경변수. IntelliJ 실행 구성에 넣어도 Gradle 빌드에는 적용되지 않습니다 |

**IntelliJ에서 직접 실행하는 서비스는 `.env` 파일을 읽지 않습니다.** `.env`는 Docker Compose가 읽는 파일이므로, IntelliJ로 띄우는 서비스에 `DB_PASSWORD`가 필요하다면 실행 구성에 직접 넣어야 합니다.

레포에 들어 있는 `.env.example` 을 복사해 `.env` 를 만들고 값을 채웁니다.

```bash
cp .env.example .env
```

`.env` 는 커밋하지 않습니다. `.gitignore` 에 이미 포함되어 있습니다.

### 4-2. 서비스가 사용하는 주요 설정값

| 키 | 두는 곳 | 값 | 무엇을 하는가 |
|---|---|---|---|
| `commonVersion` | 레포 안 `gradle.properties` | 예: `0.1.0` | 공통 모듈 버전 |
| `GPR_USER` / `GPR_TOKEN` | OS 환경변수 | GitHub 계정·토큰 | 공통 모듈 내려받기 |
| `DB_HOST` | 환경변수 (`.env`) | 개발용 PostgreSQL 주소 | 팀 공용 EC2의 주소입니다. 인스턴스를 재생성하면 바뀌므로 각자 이 값만 고칩니다 |
| `DB_PASSWORD` | 환경변수 (`.env`) | 서비스 계정 비밀번호 | 절대 커밋하지 않습니다 |
| `app.auditor.system-name` | 레포 안 `application.yml` | 기본 `SYSTEM`, ingest는 `ingest-batch`, extract는 `extract-batch` | 인증 없이 도는 배치가 감사 컬럼에 남길 이름입니다. 이 값이 없으면 배치의 INSERT가 실패합니다 |
| `app.outbox.relay.enabled` | 레포 안 `application.yml` | 한 인스턴스에서만 `true` | 미발행 이벤트를 회수하는 스케줄러를 켭니다. 여러 인스턴스에서 켜면 같은 행을 동시에 집어 순서 보장이 깨집니다 |
| `spring.jpa.hibernate.ddl-auto` | 레포 안 `application.yml` | `validate` | 스키마는 Flyway가 관리하므로 애플리케이션은 검증만 합니다. 엔티티와 DB가 어긋나면 기동에 실패합니다 |
| `spring.datasource.url` | Config 저장소의 `<서비스명>.yml` | 서비스별 DB 주소 | DB를 승격했을 때 이 값을 바꾸고 `/actuator/refresh`를 호출하면 재배포 없이 전환됩니다 |
| `spring.datasource.username` / `password` | 계정명은 Config 저장소, 비밀번호는 환경변수 | 서비스 전용 계정 | 서비스마다 자기 DB에만 접속할 수 있는 계정을 씁니다 |
| 공공데이터 `serviceKey` | 환경변수 (`.env`) | **Decoding 키 원본** | 라이브러리 자동 인코딩을 켠 상태로 사용합니다. Encoding 키를 넣으면 이중 인코딩이 되어 403이 발생합니다 |
| `LLM_BASE_URL` / `LLM_MODEL` | 환경변수 (`.env`) | 로컬 추론 서버 또는 외부 API | extract가 이 두 값만 바꿔 추론 대상을 전환합니다 |
| 소셜 로그인 `client-id` / `client-secret` | 환경변수 (`.env`) | 제공자 콘솔에서 발급받은 값 | OAuth 로그인에 사용합니다 |
| 소셜 로그인 `redirect-uri` | Config 저장소의 `<서비스명>.yml` | 로컬용·배포용 | 두 주소 모두 OAuth 제공자 콘솔에 등록되어 있어야 합니다 |
| JWT 서명 키 | Config 저장소 | auth는 개인키, gateway는 공개키 | RS256을 사용하므로 키가 둘로 나뉩니다 |

### 4-3. Config 저장소와 application.yml의 경계

- **Config 저장소의 `<서비스명>.yml`** — 여러 서비스가 공유하거나, 재배포 없이 바꿀 수 있어야 하는 값입니다. Config 서버가 이 저장소를 읽어 각 서비스에 내려줍니다
- **레포 안 `src/main/resources/application.yml`** — 그 서비스 고유의 값이며 바뀔 일이 거의 없는 것입니다 (서비스명, 포트, 동작 스위치 등)
- **환경변수** — 위 둘 어디에도 적으면 안 되는 비밀값입니다

---

## 5. 4계층 — 왜 이렇게 나누는가

모든 서비스는 `presentation / application / domain / infrastructure` 4계층으로 나뉩니다.

| 계층 | 맡는 일 | 이 계층과 관련 없는 역할 |
|---|---|---|
| **presentation** | 요청을 받아 형식을 확인하고, 결과를 응답 형태로 돌려줍니다 | 무엇이 옳은지 판단하는 일 (예시: 조건을 따져 동반 가능 여부를 계산하거나, DB를 직접 조회하는 코드) |
| **application** | 어떤 일을 어떤 순서로 할지 조율하고 결과를 조립합니다. 판단 기준 자체는 갖지 않습니다 | 판단 기준을 직접 갖는 일 (예시: 판정 규칙을 여기서 계산하거나, SQL을 작성하는 코드) |
| **domain** | 이 서비스에 어떤 개념과 규칙이 필요한지를 정의합니다. 바깥에서 가져와야 하는 것은 인터페이스로 선언만 해둡니다 | 특정 기술을 다루는 일 (예시: JPA로 조회하거나, 카프카로 발행하거나, HTTP를 호출하는 코드) |
| **infrastructure** | domain이 정의해둔 인터페이스를, 실제로 사용하는 기술 스택에 맞춰 구현합니다 | 판단 기준을 갖는 일 (예시: 어떤 조건이면 동반 가능인지를 여기서 정하는 코드) |

### 핵심 원칙

> **인터페이스는 `domain`에 선언하고, 구현은 `infrastructure`에 둡니다.**

예를 들어 verdict가 policy 서비스를 호출해야 한다면 다음과 같이 나눕니다.

- `domain/provider/PolicyProvider.java` — "정책을 가져다주는 무언가"라는 **약속**만 선언합니다
- `infrastructure/provider/PolicyProviderImpl.java` — 실제로 HTTP를 호출하는 **구현**입니다

이렇게 하면 의존 방향이 항상 `domain`을 향합니다. 그 결과 두 가지를 얻습니다.

- **`RuleEngine`을 테스트할 때 다른 서비스를 띄우지 않아도 됩니다.** 가짜 `PolicyProvider`를 넣으면 됩니다
- **통신 방식이 바뀌어도 `domain` 코드는 바뀌지 않습니다.** 구현체만 교체하면 됩니다

---

## 6. 공통 모듈 (`com.pawtrail.common`)

모든 서비스가 의존성으로 당겨쓰는 라이브러리입니다. GitHub Packages로 배포합니다.

- **넣는 것** — 기술적 관심사이면서 거의 바뀌지 않는 것
- **넣지 않는 것** — 도메인 지식, 이벤트 payload DTO, 도메인 엔티티

> 공통 모듈을 고치면 이를 사용하는 모든 서비스가 버전을 올려야 합니다. 따라서 "여러 곳에서 쓰인다"만으로는 부족하고 "앞으로 거의 바뀌지 않는다"까지 만족해야 넣습니다.

```
com.pawtrail.common
│
├── config/                                         스프링 설정 모음
│   ├── AsyncConfig.java (class)                    @EnableAsync를 켭니다. Outbox 커밋 리스너가
│   │                                               @Async로 동작해야 하므로 필요합니다. 이것이 없으면
│   │                                               카프카 발행이 메인 요청 흐름을 붙잡아 응답이 느려집니다
│   ├── AuditConfig.java (class)                    누가 만들고 고쳤는지를 자동으로 채우는 설정입니다.
│   │                                               AuditorAware가 SecurityContext에서 사용자 ID를 꺼내
│   │                                               createdBy·updatedBy에 넣습니다. 인증이 없는 배치 실행
│   │                                               시에는 app.auditor.system-name 프로퍼티 값을 씁니다.
│   │                                               JPA를 쓰지 않는 서비스에서는 로드되지 않도록 조건부입니다
│   ├── QueryDslConfig.java (class)                 JPAQueryFactory 빈을 등록합니다. 복잡한 조회 쿼리를
│   │                                               자바 코드로 작성하기 위한 도구입니다.
│   │                                               JPA를 쓰지 않는 서비스에서는 로드되지 않도록 조건부입니다
│   ├── KafkaConsumerConfig.java (class)            이벤트를 받는 쪽 설정입니다. 카프카 헤더의 X-User-Id를
│   │                                               SecurityContext로 복원하고, JSON을 @KafkaListener
│   │                                               파라미터 타입으로 자동 변환하며, 분산 추적을 켭니다
│   ├── KafkaProducerConfig.java (class)            이벤트를 보내는 쪽 설정입니다. JSON 문자열을 그대로
│   │                                               전송하도록 하고 분산 추적을 켭니다
│   └── RestClientConfig.java (class)               서비스 간 HTTP 호출용 RestClient 빈을 등록합니다.
│                                                   @LoadBalanced를 붙여 유레카에 등록된 서비스 이름
│                                                   (예: http://policy-service)으로 호출할 수 있게 하고,
│                                                   인증 헤더 인터셉터를 끼웁니다
│
├── entity/
│   └── BaseEntity.java (abstract class)            모든 테이블이 상속하는 공통 컬럼 묶음입니다.
│                                                   createdAt·createdBy, updatedAt·updatedBy,
│                                                   deletedAt·deletedBy를 가집니다. 소프트 딜리트를 쓰므로
│                                                   실제 DELETE를 하지 않고 deletedAt에 시각을 기록합니다.
│                                                   NULL이면 살아있는 행입니다
│
├── audit/
│   └── AuditorProvider.java (class)                "지금 이 작업을 하는 주체가 누구인가"를 한 곳에서
│                                                   알려줍니다. 삭제 시 deletedBy에 넣을 값을 여기서 얻습니다.
│                                                   생성·수정은 JPA가 자동으로 채우는데 삭제만 수동이므로,
│                                                   값의 출처가 갈리지 않도록 두는 장치입니다
│
├── enums/
│   └── Role.java (enum)                            USER / ADMIN. 게이트웨이가 X-User-Role로 주입하는
│                                                   값입니다
│
├── exception/
│   ├── ErrorCode.java (interface)                  에러 코드가 가져야 할 모양만 정의합니다.
│   │                                               name, HTTP 상태, 코드 문자열, 메시지로 구성되며
│   │                                               각 서비스가 자기 enum으로 구현합니다
│   ├── CommonErrorCode.java (enum)                 모든 서비스에서 같은 뜻으로 쓰이는 에러만 담습니다.
│   │                                               VALIDATION_FAILED, UNAUTHORIZED, FORBIDDEN,
│   │                                               INTERNAL_SERVER_ERROR 등입니다
│   ├── CustomException.java (class)                의도적으로 던지는 모든 예외입니다. ErrorCode를 하나
│   │                                               물고 있으며, 핸들러가 거기서 HTTP 상태와 메시지를 꺼냅니다.
│   │                                               예외 클래스를 상태별로 나누지 않는 이유는, 상태값이 이미
│   │                                               ErrorCode에 있어 클래스가 두 번째 진실의 원천이 되면
│   │                                               둘이 어긋나도 아무도 알아채지 못하기 때문입니다
│   └── handler/
│       └── GlobalExceptionHandler.java (class)
│                                                   모든 예외를 잡아 응답 형식으로 바꾸는 곳입니다.
│                                                   핸들러는 4개입니다.
│                                                   (1) CustomException → ErrorCode의 상태로 응답
│                                                   (2) MethodArgumentNotValidException(@Valid 실패)
│                                                       → 400과 함께 필드별 오류 배열 반환
│                                                   (3) MethodArgumentTypeMismatchException
│                                                       (/places/abc처럼 타입 불일치) → 400
│                                                   (4) Exception → 500
│
├── message/                                        이벤트 발행·수신의 뼈대
│   ├── DomainEvent.java (interface)                이벤트임을 표시하는 표식입니다. 내용은 없고
│   │                                               타입 제약 용도입니다
│   ├── EventEnvelope.java (record)                 모든 이벤트를 감싸는 봉투입니다. eventId(중복 판단 키),
│   │                                               eventType, aggregateId, occurredAt, payload로 구성됩니다.
│   │                                               봉투는 공통에 두지만 payload 안쪽 DTO는 각 서비스가
│   │                                               따로 정의합니다. 결합을 피하기 위함입니다
│   ├── CommonKafkaEventPublisher.java (class)
│   │                                               서비스가 이벤트를 발행할 때 호출하는 입구입니다.
│   │                                               카프카로 바로 보내지 않고 outbox 테이블에 저장합니다.
│   │                                               이때 SecurityContext의 사용자 정보와 traceId를 함께
│   │                                               저장합니다. 나중에 별도 스레드가 발행할 시점에는
│   │                                               요청 컨텍스트가 이미 사라지기 때문입니다
│   │
│   ├── outbox/                                     "DB에는 저장됐는데 이벤트는 나가지 않았다"를 막는 장치
│   │   ├── OutboxMessage.java (entity)             발행 대기 중인 이벤트 한 건입니다. 비즈니스 데이터와
│   │   │                                           같은 트랜잭션으로 저장되므로 둘 다 되거나 둘 다 안 됩니다
│   │   ├── OutboxStatus.java (enum)                INIT(대기) / PUBLISHED(발행 완료) / FAILED
│   │   ├── OutboxRepository.java (interface)
│   │   │                                           미발행 건을 조회합니다. 같은 aggregateId에 대해
│   │   │                                           앞선 미발행 건이 있는지도 확인해 순서를 보장합니다
│   │   ├── OutboxInternalEvent.java (class)        스프링 내부 신호입니다. "커밋이 끝났으니 발행하라"
│   │   ├── OutboxCommitListener.java (class)
│   │   │                                           트랜잭션 커밋 직후 비동기로 발행을 시작합니다.
│   │   │                                           정상 경로는 여기서 처리되므로 지연이 거의 없습니다
│   │   ├── OutboxMessageProcessor.java (class)
│   │   │                                           실제 카프카 발행을 수행합니다. 저장해둔 사용자 정보를
│   │   │                                           X-User-Id 등 헤더로 복원하고 상태를 PUBLISHED로 바꿉니다
│   │   └── OutboxRelay.java (class)                놓친 건을 회수하는 안전망 스케줄러입니다(10초 주기).
│   │                                               @ConditionalOnProperty로 한 인스턴스에서만 실행합니다.
│   │                                               여러 인스턴스가 동시에 돌면 같은 행을 집어
│   │                                               순서 보장 로직이 깨지기 때문입니다
│   │
│   └── inbox/                                      "같은 이벤트를 두 번 처리했다"를 막는 장치
│       ├── InboxMessage.java (entity)              처리한 eventId 기록입니다. UNIQUE 제약이
│       │                                           최후의 방어선입니다
│       ├── InboxRepository.java (interface)        해당 eventId를 이미 처리했는지 조회합니다
│       └── InboxProcessor.java (class)             processOnce(eventId, 로직) 형태로 사용합니다.
│                                                   기록과 비즈니스 로직을 한 트랜잭션으로 묶어
│                                                   "처리했다고 기록했는데 실제로는 실패"를 막습니다
│
├── response/
│   ├── CommonApiResponse.java (class)              모든 API 응답의 겉껍데기입니다.
│   │                                               { code, message, data, traceId }
│   │                                               traceId는 성공 응답에도 싣습니다. 문의가 들어왔을 때
│   │                                               해당 요청을 분산 추적에서 바로 찾기 위함입니다
│   └── PageResponse.java (record)                  목록 응답에서 data 안에 들어가는 형태입니다.
│                                                   { content: [...], page: { number, size,
│                                                     totalElements, totalPages } }
│                                                   총 건수 표시와 offset 페이징에 필요합니다
│
├── security/
│   ├── config/SecurityConfig.java (class)          스프링 시큐리티 기본 설정입니다
│   ├── filter/AuthenticationFilter.java (class)
│   │                                               게이트웨이가 넣어준 X-User-Id·X-User-Role 헤더를 읽어
│   │                                               SecurityContext를 채웁니다. 뒤쪽 서비스는 JWT를 직접
│   │                                               다루지 않습니다. 토큰 검증은 게이트웨이에서 이미 끝났습니다
│   ├── interceptor/
│   │   ├── RestClientAuthInterceptor.java (class)
│   │   │                                           서비스가 다른 서비스를 호출할 때 X-User-Id를 헤더에
│   │   │                                           실어줍니다. 이것이 없으면 호출받은 쪽이 요청자를 알 수
│   │   │                                           없어 감사 컬럼이 시스템 계정으로 기록됩니다.
│   │   │                                           traceparent는 넣지 않습니다. 분산 추적 라이브러리가
│   │   │                                           자동 처리하며, 직접 넣으면 트레이스가 갈라집니다
│   │   └── KafkaSecurityInterceptor.java (class)
│   │                                               카프카 헤더의 사용자 정보를 SecurityContext로
│   │                                               복원합니다. 컨슈머는 HTTP 요청 밖에서 실행되어
│   │                                               컨텍스트가 비어 있습니다
│   ├── principal/CustomUserPrincipal.java (class)
│   │                                               SecurityContext에 담기는 사용자 정보 객체입니다
│   └── annotation/CurrentUser.java (annotation)
│                                                   컨트롤러에서 사용자를 주입받는 애노테이션입니다
│
└── src/main/resources/
    ├── META-INF/spring/
    │   └── org.springframework.boot.autoconfigure.AutoConfiguration.imports
    │                                               위 config들을 자동 설정으로 등록합니다.
    │                                               조건부 로딩과 짝이 되며, 이 방식을 쓰면 서비스의
    │                                               컴포넌트 스캔 범위와 무관하게 로드됩니다
    └── db/migration/common/
        ├── V1__outbox.sql                          outbox 테이블입니다. 공통이므로 V1~V19 대역을 씁니다
        └── V2__inbox.sql                           inbox 테이블입니다
```

---

## 7. DB를 가진 서비스 (place 예시)

10개 서비스가 이 형태입니다. 아래 트리에서 `place`를 자기 도메인명으로 바꿔 읽으면 됩니다.

```
com.pawtrail.place
│
├── PlaceApplication.java (class)                   진입점입니다. 공통 모듈을 함께 스캔하도록 지정합니다.
│                                                   @SpringBootApplication(scanBasePackages =
│                                                       {"com.pawtrail.place", "com.pawtrail.common"})
│                                                   @EntityScan(basePackages = { 위와 동일 })
│                                                   @EnableJpaRepositories(basePackages = { 위와 동일 })
│                                                   공통 모듈을 함께 지정하는 이유는 BaseEntity와
│                                                   Outbox·Inbox 엔티티가 그쪽에 있기 때문입니다
│
├── presentation/                                   ── HTTP를 받는 층 ──
│   ├── PlaceController.java (class)                /api/v1/places — 브라우저가 호출하는 공개 API입니다
│   ├── PlaceInternalController.java (class)
│   │                                               /internal/places — 다른 서비스만 호출하는 API입니다.
│   │                                               게이트웨이가 라우팅하지 않아 외부에서 도달할 수 없습니다.
│   │                                               파일을 나눠두면 내부용임이 한눈에 드러납니다
│   └── request/
│       └── PlaceCreateRequest.java (record)        요청 바디 DTO입니다. @Valid 검증 애노테이션이 붙는
│                                                   자리이며, toCommand()로 application 층 입력으로
│                                                   변환합니다
│
├── application/                                    ── 순서를 조율하는 층 ──
│   ├── service/PlaceService.java (class)           무엇을 어떤 순서로 할지 정합니다. 저장하고, 이벤트를
│   │                                               발행하고, 결과를 조립합니다. @Transactional이 붙는
│   │                                               자리입니다
│   └── dto/
│       ├── command/
│       │   └── PlaceCreateCommand.java (record)
│       │                                           서비스에 들어가는 입력입니다
│       └── response/
│           └── PlaceResponse.java (record)         서비스가 내놓는 출력입니다. 엔티티를 그대로 담지 않고
│                                                   from(엔티티) 정적 메서드로 변환하며, 컨트롤러가 이를
│                                                   그대로 반환합니다
│
├── domain/                                         ── 이 서비스가 무엇인지 ──
│   ├── model/Place.java (entity)                   데이터와 그 데이터를 바꾸는 규칙을 가집니다
│   ├── event/
│   │   ├── PlaceEventProducer.java (interface)
│   │   │                                           이벤트를 내보낸다는 약속입니다.
│   │   │                                           카프카라는 단어가 여기 나오지 않습니다
│   │   └── payload/
│   │       └── PlaceUpdatedEvent.java (record)
│   │                                               이벤트에 실을 데이터입니다. 공통 모듈에 두지 않습니다.
│   │                                               두면 발행자가 필드를 추가할 때 소비자까지 재배포해야 합니다
│   ├── exception/PlaceErrorCode.java (enum)        이 서비스만의 에러 코드입니다. ErrorCode를 구현하며
│   │                                               PLACE_NOT_FOUND 등을 정의합니다
│   ├── provider/
│   │   ├── RawDocumentData.java (record)           다른 서비스에서 받아올 데이터의 모양입니다
│   │   └── RawDocumentProvider.java (interface)
│   │                                               그 데이터를 가져다준다는 약속입니다
│   └── repository/
│       ├── PlaceRepository.java (interface)        저장·조회의 약속입니다.
│       │                                           JPA라는 단어가 여기 나오지 않습니다
│       └── dto/
│           └── PlaceSearchConditionDto.java (record)
│                                                   조회 조건·결과 전용 객체입니다
│
├── infrastructure/                                 ── 바깥과 실제로 대화하는 층 ──
│   ├── config/                                     이 서비스만의 설정입니다
│   ├── persistence/
│   │   ├── JpaPlaceRepository.java (interface)
│   │   │                                           스프링 데이터 JPA 기본 조회입니다 (findById 등)
│   │   ├── QueryDslPlaceRepository.java (class)
│   │   │                                           복잡한 조건 조회입니다. 조회가 단순한 서비스는
│   │   │                                           이 파일을 만들지 않아도 됩니다
│   │   └── PlaceRepositoryImpl.java (class)        위 둘을 조합해 domain의 인터페이스를 구현합니다
│   ├── message/kafka/
│   │   ├── consumer/
│   │   │   └── PlaceEventConsumer.java (class)
│   │   │                                           @KafkaListener입니다. 받은 이벤트를
│   │   │                                           InboxProcessor.processOnce로 감싸 처리합니다
│   │   └── producer/
│   │       └── PlaceEventProducerImpl.java (class)
│   │                                               CommonKafkaEventPublisher를 호출해 outbox에 적재합니다
│   └── provider/client/
│       ├── RawDocumentClient.java (interface)      @HttpExchange 인터페이스입니다. 다른 서비스 호출을
│       │                                           메서드 선언만으로 정의합니다
│       ├── dto/
│       │   └── RawDocumentResponse.java (record)
│       │                                           응답을 받는 DTO입니다
│       └── RawDocumentProviderImpl.java (class)
│                                                   Client를 호출해 domain의 Provider를 구현합니다.
│                                                   응답 DTO를 domain의 Data 타입으로 변환하는 자리입니다
│
└── src/main/resources/
    ├── application.yml                             서비스명, 포트, DB 접속, 유레카 등록 등을 정의합니다
    └── db/migration/
        └── V20__place.sql                          서비스별 마이그레이션은 V20부터 시작합니다.
                                                    V1~V19는 공통 모듈이 사용하는 대역입니다
```

---

## 8. DB가 없는 서비스 (verdict)

verdict와 congestion이 여기 해당합니다. 위 형태에서 저장 관련 계층이 통째로 빠집니다.

```
com.pawtrail.verdict
│
├── VerdictApplication.java (class)                 @EntityScan과 @EnableJpaRepositories를 쓰지 않습니다.
│                                                   사용하면 JPA가 필수가 되어 기동에 실패합니다.
│                                                   @SpringBootApplication(scanBasePackages =
│                                                       {"com.pawtrail.verdict", "com.pawtrail.common"})
│
├── presentation/
│   ├── VerdictController.java (class)              /api/v1/places/{id}/verdict — 상세 화면의 판정입니다
│   └── VerdictInternalController.java (class)
│                                                   /internal/verdicts/batch, /summary — 목록과 카운트
│                                                   화면을 위해 search와 user가 호출합니다
│
├── application/
│   ├── service/VerdictService.java (class)         재료를 모으는 층입니다. policy와 pet을 호출해 가져오고
│   │                                               RuleEngine에 넘긴 뒤 응답을 만듭니다
│   └── dto/
│
├── domain/
│   ├── model/
│   │   ├── Verdict.java (record)                   판정 결과의 모양입니다
│   │   └── Reason.java (record)                    항목별 이유와 근거입니다
│   ├── engine/RuleEngine.java (class)              이 서비스의 핵심입니다. f(정책, 프로필) → 판정을
│   │                                               수행합니다. 바깥을 전혀 모르는 순수 함수라 단위 테스트가
│   │                                               쉽고, "8kg + 10kg 이하 → 가능", "안내견 단독 표기 → 불가"
│   │                                               같은 판정 케이스를 테스트로 고정할 수 있습니다
│   ├── exception/VerdictErrorCode.java (enum)
│   └── provider/
│       ├── PolicyData.java (record)                policy에서 받아올 조건 데이터의 모양입니다
│       ├── PolicyProvider.java (interface)         조건을 가져다준다는 약속입니다
│       ├── PetData.java (record)                   pet에서 받아올 프로필 데이터의 모양입니다
│       └── PetProvider.java (interface)            프로필을 가져다준다는 약속입니다
│
├── infrastructure/
│   ├── config/RedisConfig.java (class)             판정 결과 캐시 설정입니다
│   ├── cache/VerdictCacheStore.java (class)        캐시 키를 프로필 전체가 아니라 판정에 쓰이는 값의
│   │                                               조합(체중, 이동장, 유모차, 견종, 접종)으로 잡습니다
│   ├── message/kafka/consumer/
│   │   └── VerdictCacheEvictConsumer.java (class)
│   │                                               policy.changed와 pet.profile.updated를 받아
│   │                                               해당 캐시를 삭제합니다
│   └── provider/client/
│       ├── PolicyClient.java (interface)           @HttpExchange 인터페이스입니다
│       ├── PetClient.java (interface)              @HttpExchange 인터페이스입니다
│       ├── PolicyProviderImpl.java (class)         domain의 PolicyProvider를 구현합니다
│       └── PetProviderImpl.java (class)            domain의 PetProvider를 구현합니다
│
└── src/main/resources/application.yml

없는 것    persistence/                                DB를 사용하지 않습니다
           db/migration/                            테이블이 없습니다
           domain/event/                            이벤트를 발행하지 않고 받기만 합니다
           inbox 사용                                 아래 설명을 참고합니다
```

### 무상태 서비스는 Inbox를 사용하지 않습니다

InboxProcessor는 처리 이력을 DB에 남겨야 하는데 이 서비스들에는 테이블이 없습니다.

다만 문제가 되지 않습니다. 이 서비스들이 이벤트를 받아 수행하는 일이 **캐시 키 삭제**뿐이므로, 같은 이벤트를 여러 번 받아도 결과가 같습니다. 중복 방지 장치가 애초에 필요하지 않은 작업입니다.

---

## 9. 서비스별 형태 분류

| 구분 | 서비스 | 소유 DB |
|---|---|---|
| **DB 있음** | auth | auth_db |
| | user | user_db |
| | pet | pet_db |
| | place | place_db |
| | policy | policy_db |
| | search | search_db (검색 색인) |
| | ingest | raw_db |
| | route | vet_db (동물병원) |
| | report | report_db |
| | notification | notif_db |
| **DB 없음** | verdict | 무상태 순수 계산 |
| | congestion | Redis 캐시만 사용 |
| **별도 판단** | extract | 소유 DB 없이 /internal API로만 접근합니다. 다만 Spring Batch가 실행 이력 테이블을 요구하므로 이 부분만 별도로 정합니다 |
| **다른 형태** | gateway / config / eureka | 도메인 서비스가 아니므로 4계층 구조를 따르지 않습니다 |

### 이벤트 발행·수신 현황

| 서비스 | outbox (발행) | inbox (수신) |
|---|---|---|
| ingest | place.ingested | — |
| place | place.updated | place.ingested |
| policy | policy.changed | place.updated, report.submitted |
| search | — | place.updated, policy.changed |
| pet | pet.profile.updated | account.withdrawn |
| report | report.submitted | account.withdrawn |
| auth | account.withdrawn | — |
| user | — | account.withdrawn |
| notification | — | policy.changed, report.submitted, account.withdrawn |
| verdict | — | policy.changed, pet.profile.updated (inbox 미사용) |

---

## 10. Spring Boot 4 에서 달라진 것

### 10-1. 애노테이션 패키지 이동

`@EntityScan` 의 패키지가 바뀌었습니다. 옛 경로로 import 하면 `package ... does not exist` 오류가 납니다.

```java
// Spring Boot 3 (이제 존재하지 않습니다)
import org.springframework.boot.autoconfigure.domain.EntityScan;

// Spring Boot 4
import org.springframework.boot.persistence.autoconfigure.EntityScan;
```

### 10-2. 스타터 이름 변경

Spring Boot 4 에서 코드베이스가 모듈 단위로 나뉘면서 스타터 이름도 바뀌었습니다. 옛 이름도 아직 해석되지만 새 이름을 씁니다.

| Spring Boot 3 | Spring Boot 4 |
|---|---|
| `spring-boot-starter-web` | `spring-boot-starter-webmvc` |
| `org.flywaydb:flyway-core` 직접 추가 | `spring-boot-starter-flyway` |
| `org.springframework.kafka:spring-kafka` | `spring-boot-starter-kafka` |
| micrometer 브리지 + zipkin-reporter 조합 | `spring-boot-starter-zipkin` |
| `spring-boot-starter-test` 하나 | `-webmvc-test`, `-data-jpa-test` 등 모듈별로 분리 |

Flyway 는 스타터로 넣어야 Hibernate 의 스키마 검증보다 먼저 실행됩니다. `flyway-core` 만 직접 넣으면 검증이 먼저 돌아 테이블이 없다는 오류가 납니다.

### 10-3. Spring Cloud Gateway 아티팩트명 변경

게이트웨이를 다루게 될 때 필요한 내용입니다.

| 이전 | 현재 |
|---|---|
| `spring-cloud-starter-gateway` | `spring-cloud-starter-gateway-server-webflux` |
| `spring-cloud-starter-gateway-mvc` | `spring-cloud-starter-gateway-server-webmvc` |

프로퍼티 접두사도 `spring.cloud.gateway.server.webflux.*` 형태로 바뀌었습니다. **옛 접두사를 쓰면 오류 없이 무시되므로** 설정이 안 먹을 때 이 부분을 확인합니다.
