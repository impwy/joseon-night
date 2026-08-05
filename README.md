# 조선 야행

조선 다크 판타지를 배경으로 한 독자적인 로컬 생존 액션 게임입니다. JavaFX 화면, Spring Boot 애플리케이션 조립과 Armeria 상태 API를 하나의 JVM에서 실행합니다.

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
- Armeria 1.40.0
- JavaFX 25.0.4
- ArchUnit 1.4.2, SpotBugs Plugin 6.5.10

```text
joseon-night
├── game-core       순수 Java 게임 규칙과 불변 상태
└── desktop-app     JavaFX, Spring Boot, Armeria 어댑터
```

`game-core`는 JavaFX, Spring과 Armeria에 의존하지 않습니다. `desktop-app`이 core를 직접 호출하므로 한 프레임을 처리할 때 HTTP를 거치지 않습니다. Armeria는 JavaFX가 발행한 최신 읽기 전용 게임 상태만 제공합니다.

## 실행

JDK 25가 필요합니다. 프로젝트 루트에서 다음 명령을 실행합니다.

```bash
./gradlew :desktop-app:run
```

기본 창은 1280×720이며 크기를 조절할 수 있습니다. 이동 키는 `W`, `A`, `S`, `D`입니다.

Armeria는 로컬 주소 `127.0.0.1:8080`에만 열립니다.

```bash
curl http://127.0.0.1:8080/internal/healthcheck
curl http://127.0.0.1:8080/api/v1/game/status
```

두 번째 API는 게임 단계, 남은 시간, 레벨, 경험치, 적 수와 처치 수를 반환합니다.

## 검증

```bash
./gradlew clean check
```

이 명령은 단위·통합·아키텍처 테스트와 SpotBugs 정적 분석을 실행합니다. GitHub Actions도 push와 Pull Request에서 Temurin 25로 같은 명령을 실행합니다. JavaFX GUI는 CI에서 실행하지 않으며 macOS 로컬 환경에서 수동으로 확인합니다.

## 문서

- [개발 가이드](개발가이드.md)
- [개발 계획](개발계획.md)
- [도메인 모델](도메인모델.md)
- [화면 디자인](화면디자인.md)
- [자산 제작 기록](ASSETS.md)

## 1차 범위 밖

JPA, 데이터베이스, Redis, Kafka, Spring Security, 회원, 상점, 영구 저장, 보물상자, 무기 진화, 추가 캐릭터·무기·적, 오디오와 설치 패키징은 첫 수직 기능에 포함하지 않습니다.

## 저작권과 라이선스

원작의 코드, 명칭, 이미지와 음악을 사용하지 않습니다. 이 저장소의 코드와 프로젝트에서 직접 제작한 자산은 [MIT License](LICENSE)로 배포합니다.
