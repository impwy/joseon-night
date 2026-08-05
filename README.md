# 조선 야행

조선 다크 판타지를 배경으로 한 독자적인 로컬 생존 액션 게임입니다. JavaFX 프런트엔드와 Spring Boot·Armeria 백엔드를 각각 실행하며, 데스크톱 전용 API로 통신합니다.

현재 목표는 5분짜리 첫 수직 기능입니다. 도깨비 사냥꾼으로 그림자 도깨비를 피하며 봉인 부적을 자동 발사하고, 혼불을 모아 레벨업한 뒤 5분을 버티면 승리합니다. 적과 한 번이라도 닿으면 즉시 패배합니다.

## 첫 콘텐츠

| 구분 | 콘텐츠 | 동작 |
| --- | --- | --- |
| 캐릭터 | 도깨비 사냥꾼 | WASD 이동, 적 접촉 시 즉사 |
| 무기 | 봉인 부적 | 가장 가까운 적에게 자동 발사 |
| 적 | 그림자 도깨비 | 플레이어를 직선 추적 |
| 경험치 | 혼불 | 적 사망 시 생성, 범위 안에서 수집 |
| 맵 | 달빛 폐허 | 반복 타일과 플레이어 중심 카메라 |

레벨업하면 피해량, 공격 주기, 발사체 수, 이동 속도, 경험치 흡수 범위 중 서로 다른 보상 3개가 제시됩니다. 선택하는 동안 시간과 전투가 모두 멈춥니다.

## 기술 구성

- Java 25
- Gradle Wrapper 9.6.1
- Spring Boot 4.1.0
- Spring Data JPA, PostgreSQL
- Spring Data Redis
- Spring Kafka, Apache Kafka
- Spring Security Core·Crypto
- Bean Validation
- Armeria 1.40.0
- JavaFX 25.0.4
- Docker Compose, H2(테스트)
- ArchUnit 1.4.2, SpotBugs Plugin 6.5.10

```text
joseon-night
├── game-core       도메인·애플리케이션·어댑터와 Spring Boot·Armeria 백엔드
└── desktop-app     JavaFX 화면과 DesktopApiClient 프런트엔드
```

`game-core`는 Splearn과 같은 헥사고날 패키지 구조를 사용합니다. 도메인·애플리케이션과 `adapter.desktopapi`, 향후 저장·메시징·보안 어댑터를 함께 소유하며 JPA·PostgreSQL·Redis·Kafka·Bean Validation 설정도 이 모듈에만 둡니다. 테이블이 필요한 도메인은 해당 도메인 모델에 `@Entity`를 적용할 수 있고, 현재 60Hz `GameSession`은 메모리 상태로 유지합니다.

`desktop-app` 운영 코드는 `game-core` 프로젝트에 컴파일·런타임 의존하지 않습니다. JavaFX 화면은 Armeria 클라이언트를 사용하는 `DesktopApiClient`로 입력과 고정 시간 갱신을 보내고, 반환된 불변 화면 상태만 그립니다. 현재 웹 화면은 없으며, 추후 React 같은 웹 프런트엔드가 추가되면 `game-core`의 별도 `WebGameApi` 어댑터를 통해 같은 애플리케이션 기능을 사용합니다.

## 실행

JDK 25와 실행 중인 Docker Desktop이 필요합니다. 첫 번째 터미널에서 백엔드를 시작한 뒤 두 번째 터미널에서 데스크톱 화면을 시작합니다.

```bash
./gradlew :game-core:bootRun
```

```bash
./gradlew :desktop-app:run
```

`GameCoreApplication`이 PostgreSQL·Redis·Kafka Compose 서비스를 준비하고 `127.0.0.1:8080`에 Armeria를 엽니다. `DesktopApplication`은 1280×720 크기 조절 가능 창을 열며 이동 키는 `W`, `A`, `S`, `D`입니다.

인프라 상태는 다음 명령으로 확인할 수 있습니다.

```bash
docker compose -f game-core/compose.yaml ps
```

PostgreSQL은 `127.0.0.1:15432`, Redis는 `127.0.0.1:16379`, Kafka는 `127.0.0.1:29092`에만 노출됩니다. 기존 로컬 서비스와 충돌하지 않도록 전용 호스트 포트를 사용합니다.

Docker Compose를 사용할 때는 [game-core Compose](game-core/compose.yaml)의 접속값이 적용됩니다. 외부 인프라에 연결하려면 `SPRING_DOCKER_COMPOSE_ENABLED=false`로 Compose를 끄고 [game-core 설정](game-core/src/main/resources/application.yml)에 정의된 `JOSEON_NIGHT_DB_*`, `JOSEON_NIGHT_REDIS_*`, `JOSEON_NIGHT_KAFKA_*` 환경 변수를 설정합니다.

IDE에서 `GameCoreApplication`을 직접 실행할 때는 작업 디렉터리를 저장소 루트로 지정합니다. 다른 작업 디렉터리를 사용한다면 `JOSEON_NIGHT_COMPOSE_FILE`에 `game-core/compose.yaml`의 경로를 지정합니다. 데스크톱이 다른 백엔드를 호출해야 하면 `JOSEON_NIGHT_API_URL`을 설정합니다.

Armeria는 로컬 주소 `127.0.0.1:8080`에만 열립니다.

```bash
curl http://127.0.0.1:8080/internal/healthcheck
curl http://127.0.0.1:8080/api/v1/game/status
```

상태 API는 게임 단계와 HUD뿐 아니라 렌더링에 필요한 플레이어·적·투사체·혼불 위치와 레벨업 선택지를 반환합니다. 시작, 입력, 고정 시간 갱신, 레벨업 선택도 같은 `/api/v1/game` 아래의 데스크톱 전용 API로 처리합니다.

## 검증

```bash
./gradlew clean check
```

이 명령은 단위·통합·아키텍처 테스트, 실제 Core–Desktop Armeria 계약 테스트와 SpotBugs 정적 분석을 실행합니다. GitHub Actions도 push와 Pull Request에서 Temurin 25로 같은 명령을 실행합니다. JavaFX GUI는 CI에서 실행하지 않으며 macOS 로컬 환경에서 수동으로 확인합니다.

## 문서

- [개발 가이드](개발가이드.md)
- [개발 계획](개발계획.md)
- [도메인 모델](도메인모델.md)
- [화면 디자인](화면디자인.md)
- [자산 제작 기록](ASSETS.md)

## 1차 범위 밖

회원, 실제 로그인·인가 정책, 다중 사용자 게임 세션, 상점, 게임 저장, 랭킹, Kafka 이벤트, 보물상자, 무기 진화, 추가 캐릭터·무기·적, 오디오와 설치 패키징은 첫 수직 기능에 포함하지 않습니다. PostgreSQL·JPA·Redis·Kafka와 보안 기반은 `game-core`에 포함하지만 현재는 로컬 사용자 한 명의 메모리 `GameSession`만 처리하며, 아직 영속 저장 대상 도메인과 메시지 흐름은 추가하지 않았습니다.

## 저작권과 라이선스

원작의 코드, 명칭, 이미지와 음악을 사용하지 않습니다. 이 저장소의 코드와 프로젝트에서 직접 제작한 자산은 [MIT License](LICENSE)로 배포합니다.
