# ============================================
# Stage 1: Build
# Gradle + JDK 환경에서 애플리케이션 빌드
# ============================================
FROM gradle:8.14-jdk17 AS builder

WORKDIR /app

# 의존성 파일 먼저 복사 (Docker 캐시 활용)
# 의존성이 변경되지 않으면 다음 빌드 시 캐시 재사용
COPY build.gradle settings.gradle ./
COPY gradle ./gradle
COPY buildSrc ./buildSrc

# 의존성 다운로드 (캐시 레이어 생성)
# --no-daemon: 컨테이너 환경에서는 데몬 불필요
# || true: 일부 의존성 해결 실패해도 계속 진행
RUN gradle dependencies --no-daemon || true

# 소스 코드 복사 (자주 변경되므로 마지막에 복사)
COPY src ./src

# JAR 파일 빌드
# -x test: 테스트는 CI에서 이미 실행했으므로 스킵
RUN gradle bootJar --no-daemon -x test

# ============================================
# Stage 2: Runtime
# JRE만 포함된 경량 이미지로 실행
# ============================================
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# 헬스체크용 curl 설치 (Alpine 기본 이미지에 없음)
RUN apk add --no-cache curl

# 보안: root 대신 일반 사용자로 실행
# -S: 시스템 계정 (로그인 불가, 서비스용)
# -g/-u 1001: 충돌 방지를 위한 고정 ID
RUN addgroup -g 1001 -S appgroup && \
    adduser -u 1001 -S appuser -G appgroup

# builder 스테이지에서 JAR 파일만 복사
# 빌드 도구(Gradle, JDK)는 포함되지 않음 -> 이미지 크기 감소
COPY --from=builder /app/build/libs/*.jar app.jar

# 파일 소유권을 appuser로 변경
RUN chown -R appuser:appgroup /app

# 이후 모든 명령을 appuser 권한으로 실행
USER appuser

# 컨테이너 상태 모니터링
# --start-period: Spring Boot 초기화 시간 고려 (60초)
# /actuator/health: Spring Boot Actuator 헬스체크 엔드포인트
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8080/actuator/health || exit 1

# 문서화: 이 컨테이너가 사용하는 포트
EXPOSE 8080

# JVM 최적화 옵션
# +UseContainerSupport: 컨테이너 메모리 제한 인식
# MaxRAMPercentage: 컨테이너 메모리의 75%를 힙으로 사용
# java.security.egd: 난수 생성 속도 향상 (시작 시간 단축)
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom"

# 컨테이너 시작 시 실행할 명령
# sh -c: 환경변수($JAVA_OPTS) 확장을 위해 쉘 사용
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
