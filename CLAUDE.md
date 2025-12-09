# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

### What is chz-scout?
chz-scout는 **치지직(Chzzk) 실시간 스트리밍 추천 Discord 챗봇**입니다. 유저 취향에 맞는 방송을 AI(GPT)로 자동 추천하고, 태그 기반 알림을 제공하는 서비스입니다.

### Service Type
- **Platform**: Discord Bot (디스코드 챗봇)
- **Target Platform**: 치지직(Chzzk) - 네이버의 실시간 스트리밍 플랫폼
- **Purpose**: 실시간 방송 검색, AI 추천, 태그 기반 알림

### Core Features

#### 1. Real-time Stream Data Collection
- 치지직 Open API를 통해 **생방송 정보를 주기적으로 수집**
- 수집된 데이터는 **Redis에 캐싱**하여 빠른 응답 제공
- 방송 제목, 스트리머 정보, 시청자 수, 카테고리 등 메타데이터 저장

#### 2. AI-Powered Stream Recommendation
- GPT가 Redis에 저장된 방송 정보를 분석
- **유저의 채팅 요청을 기반으로 적절한 방송 추천**
- 예: "재미있는 게임 방송 추천해줘" → GPT가 컨텍스트 기반으로 방송 선별

#### 3. Chat-based Stream Search
- 디스코드 채팅으로 원하는 방송 검색 가능
- 자연어 처리를 통한 직관적인 검색 경험
- 실시간 방송 목록 조회 및 필터링

#### 4. Tag-based Notification System
- 유저가 관심 태그 설정 (예: "롤", "FPS", "음악방송")
- 해당 태그의 방송 시작 시 **자동 알림 전송**
- 태그 설정은 **웹 UI로 유도** (Discord 명령어 → 웹 페이지 링크 제공)

#### 5. Web Interface
- 태그 관리를 위한 웹 UI 제공
- 유저별 맞춤 설정 페이지
- Discord 계정 연동

### System Architecture

```
[치지직 API] → [Spring Scheduler] → [Redis Cache]
                                         ↓
[Discord Bot] ← [GPT Analysis] ← [Redis Cache]
      ↓
[User Interaction] → [Tag Settings] → [Web UI]
      ↓
[Notification] ← [Tag Matching] ← [MySQL]
```

### Data Flow

1. **Data Collection Layer** (infra/chzzk)
   - 치지직 API 호출 → 생방송 정보 수집
   - Redis에 방송 데이터 저장 (TTL 설정)

2. **Recommendation Layer** (application/service)
   - GPT API 호출 시 Redis 데이터 주입
   - 유저 요청 분석 및 방송 추천

3. **Notification Layer** (application/service)
   - 태그 매칭 로직 실행
   - 조건 충족 시 Discord 알림 전송

4. **Persistence Layer** (infrastructure)
   - MySQL: 유저 정보, 태그 설정, 알림 이력
   - Redis: 실시간 방송 캐시, 세션 관리

### Infrastructure

- **CI/CD**: GitHub Actions (자동 빌드, 테스트, 배포)
- **Caching**: Redis (방송 정보, 세션)
- **Database**: MySQL (영속 데이터)
- **External APIs**:
  - 치지직 Open API (방송 정보)
  - Discord Bot API (메시지 전송)
  - OpenAI GPT API (추천 로직)

### Domain Boundaries

이 프로젝트는 클린 아키텍처를 따르며, 다음 도메인으로 구성됩니다:

1. **stream**: 방송 정보 수집 및 조회
2. **tag**: 태그 관리 및 매칭
3. **notification**: 알림 발송 로직
4. **user**: 사용자 관리 (Discord 연동)
5. **infra/chzzk**: 치지직 API 통합
6. **infra/discord**: Discord Bot 통합

### Key Design Decisions

- **Redis 사용 이유**: 방송 정보는 휘발성이 높고 실시간성이 중요하므로 캐싱 필수
- **GPT 활용 이유**: 단순 키워드 매칭보다 자연어 이해 기반 추천이 사용자 경험 향상
- **웹 UI 분리 이유**: Discord UI 한계로 복잡한 태그 설정은 웹으로 제공
- **클린 아키텍처 채택**: 외부 API 의존성 격리 및 테스트 용이성 확보

## Tech Stack

### Backend Framework
- Java 17
- Spring Boot 4.0.0
- Spring Data JPA
- Spring Security
- Spring Web MVC
- Lombok
- Gradle

### Data Storage
- **MySQL**: 유저 정보, 태그 설정, 알림 이력 등 영속 데이터 저장
- **Redis**: 실시간 방송 정보 캐싱, 세션 관리

### External Integrations
- **Chzzk Open API**: 치지직 플랫폼의 실시간 방송 정보 수집
- **Discord Bot API**: Discord 메시지 송수신 및 명령어 처리
- **OpenAI GPT API**: 자연어 기반 방송 추천 로직

### DevOps
- **CI/CD**: GitHub Actions (자동 빌드, 테스트, 배포 파이프라인)
- **Branching Strategy**: GitHub Flow

## Git Branching Strategy (GitHub Flow)

이 프로젝트는 **GitHub Flow** 브랜칭 전략을 사용합니다.

### 기본 원칙

1. **main 브랜치는 항상 배포 가능한 상태 유지**
2. **새로운 작업은 main에서 feature 브랜치 생성**
3. **작업 완료 후 Pull Request 생성**
4. **코드 리뷰 후 main에 머지**
5. **머지 후 즉시 배포**

### 브랜치 네이밍 규칙

```
feature/<기능명>     # 새로운 기능 개발
fix/<버그명>         # 버그 수정
hotfix/<긴급수정명>  # 프로덕션 긴급 수정
docs/<문서명>        # 문서 작업
refactor/<대상>      # 리팩토링
```

### 예시

```bash
# 기능 개발
git checkout main
git pull origin main
git checkout -b feature/stream-recommendation

# 작업 완료 후
git push origin feature/stream-recommendation
# GitHub에서 PR 생성 → 리뷰 → main 머지
```

### 워크플로우

```
main ──●──────────────────●──────────────●── (배포)
        \                /                /
         ●──●──●──●──●──● PR             /
         feature/stream-recommendation  /
                          \            /
                           ●──●──●──●PR
                           fix/login-bug
```

### PR 규칙

- PR 생성 시 최소 1명의 리뷰어 지정
- CI 통과 필수 (빌드, 테스트, 포맷팅 검사)
- Squash and Merge 권장 (커밋 히스토리 정리)

## Build Commands

```bash
# Build project
./gradlew build

# Run application
./gradlew bootRun

# Run tests
./gradlew test

# Run single test class
./gradlew test --tests "com.example.demo.SomeTest"

# Run single test method
./gradlew test --tests "com.example.demo.SomeTest.methodName"

# Clean build
./gradlew clean build
```

## Useful Command Combinations

```bash
# 포맷팅 + 테스트 + 빌드 (권장)
./gradlew spotlessApply test build

# 포맷팅 검사 + 커버리지 검증
./gradlew spotlessCheck jacocoTestCoverageVerification

# 전체 검증 (CI용)
./gradlew clean spotlessCheck test jacocoTestReport build

# 빠른 빌드 (테스트 제외)
./gradlew build -x test
```

## Project Structure (클린 아키텍처 기반)

```
src/main/java/com/vatti/chzscoute/backend/
├── example/                    # 샘플 도메인
│   ├── presentation/           # Controller
│   ├── application/
│   │   ├── usecase/            # UseCase 인터페이스
│   │   └── service/            # UseCase 구현체
│   ├── infrastructure/         # Repository 인터페이스 + 구현체
│   └── domain/
│       ├── entity/             # Entity
│       ├── dto/                # DTO
│       └── event/              # Domain Event
├── stream/
│   ├── presentation/
│   ├── application/
│   │   ├── usecase/
│   │   └── service/
│   ├── infrastructure/
│   └── domain/
│       ├── entity/
│       ├── dto/
│       └── event/
├── tag/
│   └── ...
├── global/
│   ├── config/                 # 설정 클래스
│   ├── entity/                 # 공통 Entity (BaseEntity)
│   ├── exception/              # 공통 예외 처리
│   ├── response/               # 공통 응답 형식
│   └── util/                   # 유틸리티
└── infra/
    ├── chzzk/                  # 치지직 API 연동
    └── discord/                # 디스코드 봇 연동
```

## Commit Convention (Conventional Commits)

```
<type>(<scope>): <subject>

<body>

<footer>
```

### Types
- `feat`: 새로운 기능 추가
- `fix`: 버그 수정
- `docs`: 문서 변경
- `style`: 코드 포맷팅 (기능 변경 없음)
- `refactor`: 리팩토링
- `test`: 테스트 추가/수정
- `chore`: 빌드, 설정 파일 변경

### Examples
```
feat(stream): 실시간 스트리머 조회 API 추가
fix(user): 회원가입 시 중복 이메일 검증 오류 수정
refactor(tag): 태그 매칭 알고리즘 개선
```

## Code Generation Guide

### Entity 생성 시 (domain/entity/)
```java
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "테이블명")
public class EntityName extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 정적 팩토리 메서드 사용
    public static EntityName create(...) {
        ...
    }
}
```

### Repository 인터페이스 (domain/repository/)
```java
public interface UserRepository {
    User save(User user);
    Optional<User> findById(Long id);
    List<User> findAll();
}
```

### Repository 구현체 (infrastructure/)
```java
@Repository
@RequiredArgsConstructor
public class UserRepositoryImpl implements UserRepository {

    private final UserJpaRepository userJpaRepository;

    @Override
    public User save(User user) {
        return userJpaRepository.save(user);
    }
}
```

### UseCase 인터페이스 (application/usecase/)
```java
public interface CreateUserUseCase {
    UserResponse execute(CreateUserRequest request);
}
```

### Service - UseCase 구현체 (application/service/)
```java
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CreateUserService implements CreateUserUseCase {

    private final UserRepository userRepository;

    @Override
    @Transactional
    public UserResponse execute(CreateUserRequest request) {
        User user = User.create(request.name(), request.email());
        return UserResponse.from(userRepository.save(user));
    }
}
```

### Controller (presentation/)
```java
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/users")
public class UserController {

    private final CreateUserUseCase createUserUseCase;
    private final GetUserUseCase getUserUseCase;

    @PostMapping
    public ApiResponse<UserResponse> create(@RequestBody CreateUserRequest request) {
        return ApiResponse.success(createUserUseCase.execute(request));
    }

    @GetMapping("/{id}")
    public ApiResponse<UserResponse> get(@PathVariable Long id) {
        return ApiResponse.success(getUserUseCase.execute(id));
    }
}
```

### Domain Event (domain/event/)
```java
public record UserCreatedEvent(
    Long userId,
    String email,
    LocalDateTime createdAt
) {
    public static UserCreatedEvent of(User user) {
        return new UserCreatedEvent(user.getId(), user.getEmail(), LocalDateTime.now());
    }
}
```

## Prohibited Patterns

### 하지 말아야 할 것들

1. **Entity에 @Setter 사용 금지**
   - 대신 의미있는 비즈니스 메서드 사용 (예: `user.changePassword()`)

2. **Controller에서 직접 Repository 호출 금지**
   - 반드시 Service 계층을 통해 접근

3. **Native Query 남용 금지**
   - QueryDSL 또는 JPQL 우선 사용

4. **하드코딩된 값 금지**
   - 설정값은 application.yml 또는 상수 클래스로 관리

5. **비밀번호/API 키 직접 커밋 금지**
   - 환경변수 또는 외부 설정으로 관리

6. **테스트 없이 비즈니스 로직 작성 금지**
   - Service 계층은 반드시 단위 테스트 작성

7. **과도한 상속 사용 금지**
   - 상속보다 컴포지션 우선

8. **catch 블록에서 예외 무시 금지**
   ```java
   // BAD
   catch (Exception e) { }

   // GOOD
   catch (Exception e) {
       log.error("에러 발생", e);
       throw new CustomException(...);
   }
   ```

## API Response Format

```java
// 성공 응답
{
    "success": true,
    "data": { ... },
    "error": null
}

// 실패 응답
{
    "success": false,
    "data": null,
    "error": {
        "code": "USER_NOT_FOUND",
        "message": "사용자를 찾을 수 없습니다."
    }
}
```