# 빌드는 Jenkins에서 수행하고, 이 이미지는 결과물 jar만 담습니다.
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# settings.gradle 의 rootProject.name 이 jar 이름이 되므로
# 와일드카드로 두면 서비스마다 고칠 필요가 없습니다.
COPY build/libs/*.jar app.jar

# 컨테이너 메모리 상한 대비 비율로 힙을 지정합니다.
# 지정하지 않으면 JVM이 상한을 넘겨 힙을 늘리다 로그 없이 종료됩니다.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=70"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
