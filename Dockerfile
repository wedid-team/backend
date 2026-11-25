# 배포용 이미지 (deploy/docker-compose.yml에서 빌드, 대상: EC2 t4g = linux/arm64)
#
# 빌드 단계는 빌드하는 PC의 아키텍처($BUILDPLATFORM)에서 돌리고(jar는 아키텍처와 무관),
# 실행 단계만 대상 아키텍처(linux/arm64)로 만든다.
# → EC2(arm64)에서 빌드하면 그대로 네이티브, x86 PC에서 arm64로 빌드해도 Gradle은 에뮬레이션 없이 빠르게 돈다.

# 1단계: 빌드
FROM --platform=$BUILDPLATFORM eclipse-temurin:21-jdk AS build
WORKDIR /app

# 의존성 캐시를 위해 Gradle 설정을 먼저 복사
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew && ./gradlew dependencies --no-daemon > /dev/null

COPY src ./src
# 테스트는 로컬 DB·XLSX가 필요해 이미지 빌드에서는 생략 (PR에서 로컬로 실행)
RUN ./gradlew bootJar --no-daemon -x test

# 2단계: 실행 (eclipse-temurin은 arm64 이미지를 제공)
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/build/libs/*-SNAPSHOT.jar app.jar

# JVM 메모리 옵션
# - MaxRAMPercentage=75: 힙을 컨테이너 메모리 제한(docker-compose의 mem_limit)의 75%로
# - UseSerialGC: vCPU 2개·메모리 1~2GB인 소형 인스턴스에서 GC 스레드·메모리 부담이 가장 작음
# - ExitOnOutOfMemoryError: 메모리 부족 시 멈춘 채 남지 않고 종료 → restart 정책으로 재시작
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+UseSerialGC -XX:+ExitOnOutOfMemoryError"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
