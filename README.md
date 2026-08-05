# 조선 야행

조선 다크 판타지를 배경으로 한 독자적인 생존 액션 게임입니다. JavaFX 프런트엔드와 Spring Boot 백엔드를 각각 실행하며, 회원 기능은 Tomcat REST API로, 실시간 게임은 Armeria WebSocket으로 통신합니다.

Google 로그인 후 도깨비 사냥꾼 또는 해금한 질풍 무녀로 5분 동안 그림자 도깨비를 피합니다. 능력 강화와 아이템 보상을 고르고 보물상자에서 진화하며, 서버가 시간·점수·승패를 계산합니다. 캐릭터 고유 스킬이 막지 않은 적 충돌은 즉시 패배입니다.

## 첫 콘텐츠

| 구분 | 콘텐츠 | 동작 |
| --- | --- | --- |
| 캐릭터 | 도깨비 사냥꾼·질풍 무녀 | 호신결계 1회 또는 이동 속도 25% 고유 스킬 |
| 아이템 | 봉인 부적 외 5종 | 자동 투사체 또는 즉시 낙뢰, 5레벨 재료 두 개로 진화 |
| 적 | 그림자 도깨비 | 플레이어를 직선 추적 |
| 경험치 | 혼불 | 적 사망 시 생성, 범위 안에서 수집 |
| 상자 | 노란 상자·보라 상자 | 아이템 강화 또는 진화, 선택 중 전체 일시정지 |
| 맵 | 달빛 폐허 | 로비의 대나무·달·풀·안개 배경과 전투의 반복 폐허 바닥 |

레벨업하면 일반 능력 강화가 최소 하나 포함되고, 아이템 획득·강화를 합친 서로 다른 선택지 최대 3개가 제시됩니다. 선택하는 동안 시간과 전투가 모두 멈춥니다.

## 기술 구성

- Java 25
- Gradle Wrapper 9.6.1
- Spring Boot 4.1.0
- Spring Data JPA, PostgreSQL
- Spring Data Redis
- Spring Kafka, Apache Kafka
- Spring Web MVC·Tomcat, Spring Security OAuth2·JWT
- Bean Validation
- Lombok 1.18.46
- Armeria 1.40.0
- JavaFX 25.0.4
- Flyway, Docker Compose, H2, PostgreSQL Testcontainers
- Instancio
- ArchUnit 1.4.2, SpotBugs Plugin 6.5.10

```text
joseon-night
├── game-core       도메인·애플리케이션·어댑터와 Spring Boot·Armeria 백엔드
└── desktop-app     JavaFX 화면과 DesktopApiClient 프런트엔드
```

`game-core`는 Splearn과 같은 실용적 헥사고날 패키지 구조를 사용합니다. JPA 포트는 `application.<도메인>.required`에서 `JpaRepository`를 직접 상속하고 Spring Data가 구현합니다. 웹 API는 `adapter.webapi/memberapi`, `rankingapi`, 인증은 `adapter.security`, Kafka는 `adapter.integration.messaging`에 둡니다. 영속 엔티티는 PostgreSQL에 저장하고 60Hz `GameSession`은 메모리에 유지합니다.

`desktop-app` 운영 코드는 `game-core` 프로젝트에 컴파일·런타임 의존하지 않습니다. 시스템 브라우저에서 Google 로그인을 마치고, JavaFX는 입력과 선택만 WebSocket으로 전송하며 반환된 불변 화면 상태를 그립니다. 추후 React 게임이 추가되면 `adapter.webapi.gameapi`가 같은 application provided 포트를 사용합니다.

## 실행

JDK 25와 실행 중인 Docker Desktop이 필요합니다. 첫 번째 터미널에서 백엔드를 시작한 뒤 두 번째 터미널에서 데스크톱 화면을 시작합니다.

```bash
./gradlew :game-core:bootRun
```

```bash
./gradlew :desktop-app:run
```

`GameCoreApplication`이 PostgreSQL·Redis·Kafka Compose 서비스를 준비하고 Tomcat을 `127.0.0.1:8080`, Armeria를 `127.0.0.1:8081`에 엽니다. `DesktopApplication`은 1280×720 크기 조절 가능 창을 열며 이동 키는 `W`, `A`, `S`, `D`입니다.

인프라 상태는 다음 명령으로 확인할 수 있습니다.

```bash
docker compose -f game-core/compose.yaml ps
```

PostgreSQL은 `127.0.0.1:15432`, Redis는 `127.0.0.1:16379`, Kafka는 `127.0.0.1:29092`에만 노출됩니다. 기존 로컬 서비스와 충돌하지 않도록 전용 호스트 포트를 사용합니다.

Docker Compose를 사용할 때는 [game-core Compose](game-core/compose.yaml)의 접속값이 적용됩니다. 외부 인프라에 연결하려면 `SPRING_DOCKER_COMPOSE_ENABLED=false`로 Compose를 끄고 [game-core 설정](game-core/src/main/resources/application.yml)에 정의된 `JOSEON_NIGHT_DB_*`, `JOSEON_NIGHT_REDIS_*`, `JOSEON_NIGHT_KAFKA_*` 환경 변수를 설정합니다. 실제 Google 로그인에는 Google client ID·secret, subject HMAC 비밀값과 RSA 공개·개인키 환경 변수가 필요하며 저장소에는 넣지 않습니다.

IDE에서 `GameCoreApplication`을 직접 실행할 때는 작업 디렉터리를 저장소 루트로 지정합니다. 다른 작업 디렉터리를 사용한다면 `JOSEON_NIGHT_COMPOSE_FILE`에 `game-core/compose.yaml`의 경로를 지정합니다. 데스크톱이 다른 백엔드를 호출해야 하면 `JOSEON_NIGHT_API_URL`을 설정합니다.

Armeria 상태 확인은 로컬 주소 `127.0.0.1:8081`에서 제공합니다.

```bash
curl http://127.0.0.1:8081/internal/healthcheck
```

게임 시작·입력·레벨업·상자 선택은 인증된 Armeria WebSocket으로 처리합니다. 클라이언트가 임의 시간이나 점수를 전송하는 REST tick API는 제공하지 않습니다.

## 검증

```bash
./gradlew clean check
```

이 명령은 단위·통합·아키텍처 테스트, 실제 Core–Desktop Armeria 계약 테스트와 SpotBugs 정적 분석을 실행합니다. GitHub Actions도 push와 Pull Request에서 Temurin 25로 같은 명령을 실행합니다. JavaFX GUI는 CI에서 실행하지 않으며 macOS 로컬 환경에서 수동으로 확인합니다.

## 문서

- [개발 가이드](개발가이드.md)
- [개발 계획](개발계획.md)
- [2차 개발 계획](docs/plans/조선-야행-2차-개발계획.md)
- [3차 개발 계획](docs/plans/조선-야행-3차-연결-UI-오디오-설정-개선계획.md)
- [4차 개발 계획](docs/plans/조선-야행-4차-도메인-낙뢰-음향-테스트-개선계획.md)
- [도메인 모델](도메인모델.md)
- [화면 디자인](화면디자인.md)
- [자산 제작 기록](ASSETS.md)

## 2차 범위 밖

상점·결제, 계정에 보관하는 보물상자, QueryDSL, 별도 문서형 NoSQL, refresh token·Keychain 로그인 유지, 설치 패키징과 실제 클라우드·TLS 배포는 2차 범위에 포함하지 않습니다. 초기 게임 세션은 단일 서버 메모리에 있으므로 수평 확장은 이후 과제로 둡니다.

## 저작권과 라이선스

원작의 코드, 명칭, 이미지와 음악을 사용하지 않습니다. 이 저장소의 코드와 프로젝트에서 직접 제작한 자산은 [MIT License](LICENSE)로 배포합니다.
