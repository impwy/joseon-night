# 조선 야행 자산 제작 기록

## 사용 원칙

- 다른 게임의 이미지, 스프라이트, 음악, 로고를 입력 또는 최종 자산으로 사용하지 않는다.
- 텍스트 프롬프트에서 시작한 독자적인 조선 다크 판타지 시안만 사용한다.
- 스프라이트는 후처리와 검수를 마친 최종 32×32 PNG로, 로비 배경은 1280×720 PNG로 저장한다.
- 캐릭터, 적, 투사체와 혼불은 투명 RGBA, 바닥은 불투명 RGB로 저장한다.
- 화면에서는 최근접 보간을 사용하며, 코드와 프로젝트에서 직접 만든 자산에는 MIT License를 적용한다. Google 브랜드 자산은 별도 사용 조건을 따른다.

## 최종 자산

| 자산 | 최종 파일 | 후처리와 검수 | 상태 |
| --- | --- | --- | --- |
| 도깨비 사냥꾼 | `desktop-app/src/main/resources/assets/sprites/player.png` | 마젠타 제거, 30px 안에 비율 유지, 32×32 RGBA. 네 모서리 알파 0과 작은 화면 실루엣 확인 | 완료 |
| 그림자 도깨비 | `desktop-app/src/main/resources/assets/sprites/enemy.png` | 마젠타 제거, 30px 안에 비율 유지, 32×32 RGBA. 플레이어와 색·실루엣 구분 확인 | 완료 |
| 봉인 부적 | `desktop-app/src/main/resources/assets/sprites/talisman.png` | 마젠타 제거, 26px 안에 비율 유지, 32×32 RGBA. 축소 후 직사각형과 금색 테두리 확인 | 완료 |
| 혼불 | `desktop-app/src/main/resources/assets/sprites/soul-flame.png` | 마젠타 제거, 22px 안에 비율 유지, 32×32 RGBA. 바닥과 투사체에서 청백색 불꽃 식별 확인 | 완료 |
| 달빛 폐허 바닥 | `desktop-app/src/main/resources/assets/sprites/ground.png` | 전체 원본을 최근접 보간으로 32×32 RGB 변환. 8×8 반복 미리보기에서 고대비 경계 없음 확인 | 완료 |
| 질풍 무녀 | `desktop-app/src/main/resources/assets/sprites/gale-maiden.png` | 마젠타 제거, 30px 안에 비율 유지, 32×32 RGBA와 투명 모서리 확인 | 완료 |
| 노란 보물상자 | `desktop-app/src/main/resources/assets/sprites/chest-yellow.png` | 마젠타 제거, 30px 안에 비율 유지, 금색 실루엣 확인 | 완료 |
| 보라 보물상자 | `desktop-app/src/main/resources/assets/sprites/chest-purple.png` | 초록색 제거, 30px 안에 비율 유지, 노란 상자와 색·잠금 장식 구분 확인 | 완료 |
| 화염 부채 | `desktop-app/src/main/resources/assets/sprites/flame-fan.png` | 초록색 제거, 28px 안에 비율 유지, 불꽃 세 갈래 확인 | 완료 |
| 벽사검 | `desktop-app/src/main/resources/assets/sprites/warding-sword.png` | 초록색 제거, 28px 안에 비율 유지, 대각선 검 실루엣 확인 | 완료 |
| 회귀 부메랑 | `desktop-app/src/main/resources/assets/sprites/returning-boomerang.png` | 초록색 제거, 28px 안에 비율 유지, 초승달형 실루엣 확인 | 완료 |
| 낙뢰 방울 | `desktop-app/src/main/resources/assets/sprites/thunder-bell.png` | 마젠타 제거, 28px 안에 비율 유지, 청백색 낙뢰 강조 확인 | 완료 |
| 혼령 호리병 | `desktop-app/src/main/resources/assets/sprites/spirit-gourd.png` | 마젠타 제거, 28px 안에 비율 유지, 혼불과 호리병 구분 확인 | 완료 |
| 달빛 대나무 로비 | `desktop-app/src/main/resources/assets/backgrounds/lobby-moonlit-courtyard.png` | 원본을 최근접 방식으로 1280×720 RGB 변환, 대나무·달·풀·안개와 중앙 UI 여백 확인 | 완료 |

투명 자산은 내장 `image_gen`으로 단색 배경 원본을 만든 뒤 설치된 `remove_chroma_key.py`의 border 자동 추출, soft matte, despill을 적용했다. Pillow 12.2.0의 최근접 보간으로 축소했으며 네 투명 모서리, 알파 범위, 픽셀 크기를 자동 검사했다.

## 외부 브랜드 자산

| 자산 | 파일 | 출처와 사용 범위 | 적용 조건 |
| --- | --- | --- | --- |
| Google Sign in G | `desktop-app/src/main/resources/assets/brand/google-g-sign-in.png` | Google 공식 사전 승인 Android + Web용 Light Square PNG @1x, 40×40. 로그인 버튼의 아이콘으로만 사용하며 게임 자산으로 변형하지 않는다. | [Google Sign in 브랜드 가이드](https://developers.google.com/identity/branding-guidelines?hl=en)를 따른다. MIT License 대상이 아니다. |

## 실제 생성 프롬프트

각 프롬프트는 참조 이미지 없이 내장 `image_gen`에 별도로 전달했다.

### 도깨비 사냥꾼

```text
Use case: stylized-concept
Asset type: original 2D game character sprite
Primary request: a single Joseon-era dokkaebi hunter for an original top-down survival game, wearing a dark navy durumagi robe, compact black gat, muted crimson sash, holding one small paper talisman
Subject: exactly one full-body human hunter, readable heroic silhouette, no separate objects
Style/medium: authentic crisp 16-bit pixel art, designed to remain readable when reduced to 32x32 pixels, limited palette, hard square pixel clusters, no smoothing
Composition/framing: centered single sprite, three-quarter top-down game view, generous empty padding, full body visible
Lighting/mood: cool moonlight, restrained mysterious mood
Color palette: navy, charcoal, ivory, muted crimson, small warm gold accent
Scene/backdrop: perfectly flat solid #ff00ff chroma-key background for removal
Constraints: background must be one uniform #ff00ff color with no gradient, shadow, texture, floor, reflection, or lighting variation; do not use #ff00ff in the subject; no text; no watermark; no logo; no frame; no sprite sheet; no resemblance to any existing game character
```

### 그림자 도깨비

```text
Use case: stylized-concept
Asset type: original 2D game enemy sprite
Primary request: a single shadow dokkaebi enemy for an original Joseon dark-fantasy top-down survival game, squat goblin-like spirit with two short uneven horns, charcoal body, tattered indigo vest, small pale blue ghost-fire eyes
Subject: exactly one full-body non-human shadow creature, compact menacing silhouette, clearly different from a human
Style/medium: authentic crisp 16-bit pixel art, designed to remain readable when reduced to 32x32 pixels, limited palette, hard square pixel clusters, no smoothing
Composition/framing: centered single sprite, three-quarter top-down game view matching a 2D action game, generous empty padding, full body visible
Lighting/mood: cool moonlight, eerie but not graphic
Color palette: charcoal, deep indigo, pale cyan eyes, tiny muted violet accent
Scene/backdrop: perfectly flat solid #ff00ff chroma-key background for removal
Constraints: background must be one uniform #ff00ff color with no gradient, shadow, texture, floor, reflection, or lighting variation; do not use #ff00ff in the subject; no text; no watermark; no logo; no frame; no sprite sheet; no resemblance to any existing game enemy
```

### 봉인 부적

```text
Use case: stylized-concept
Asset type: original 2D game projectile sprite
Primary request: a single flying Joseon-style sealing talisman projectile for an original dark-fantasy survival game, narrow ivory paper slip with a simple abstract crimson seal pattern and a small warm-gold glow along the edge
Subject: exactly one compact paper talisman, strong vertical rectangular silhouette, no readable writing
Style/medium: authentic crisp 16-bit pixel art, designed to remain readable when reduced to 16x16 or 32x32 pixels, very limited palette, hard square pixel clusters, no smoothing
Composition/framing: centered single icon in a slight diagonal flying angle, generous empty padding
Lighting/mood: subtle magical glow contained inside the silhouette
Color palette: ivory, muted crimson, dark brown, warm gold
Scene/backdrop: perfectly flat solid #ff00ff chroma-key background for removal
Constraints: background must be one uniform #ff00ff color with no gradient, shadow, texture, floor, reflection, or lighting variation; do not use #ff00ff in the subject; no legible text or symbols from a real religion; no watermark; no logo; no frame; no sprite sheet; no resemblance to any existing game weapon
```

### 혼불

```text
Use case: stylized-concept
Asset type: original 2D game experience pickup sprite
Primary request: a single tiny honbul soul-flame pickup for an original Joseon dark-fantasy survival game, a compact teardrop ghost flame with a bright ivory core and pale cyan outer flame
Subject: exactly one simple floating flame pickup, strong symmetrical silhouette, no face
Style/medium: authentic crisp 16-bit pixel art, designed to remain readable when reduced to 16x16 pixels, extremely limited palette, hard square pixel clusters, no smoothing
Composition/framing: centered single icon, generous empty padding
Lighting/mood: gentle supernatural glow contained close to the flame
Color palette: ivory core, pale cyan, muted blue, one deep navy outline
Scene/backdrop: perfectly flat solid #ff00ff chroma-key background for removal
Constraints: background must be one uniform #ff00ff color with no gradient, shadow, texture, floor, reflection, or lighting variation; do not use #ff00ff in the subject; no text; no watermark; no logo; no frame; no sprite sheet; no resemblance to any existing game pickup
```

### 달빛 폐허 바닥

```text
Use case: stylized-concept
Asset type: seamless tileable 2D game ground texture
Primary request: an original moonlit Joseon ruined-courtyard ground tile made of irregular dark stone slabs with sparse tiny dried grass between cracks
Subject: only flat ground texture viewed perfectly from above, no objects, characters, walls, props, symbols, or focal point
Style/medium: authentic crisp 16-bit pixel art, designed to tile at 32x32 pixels, limited palette, hard square pixel clusters, no smoothing
Composition/framing: orthographic top-down square texture, perfectly seamless on all four edges, evenly distributed detail
Lighting/mood: dim cool moonlight, readable but low contrast so sprites stand out
Color palette: charcoal blue, deep navy, desaturated slate, tiny muted brown grass accents
Constraints: seamless edges; no border; no vignette; no perspective; no text; no watermark; no logo; no resemblance to any existing game map
```

## 2차 추가 자산 프롬프트

아래 자산도 참조 이미지 없이 내장 `image_gen`에 각각 한 번씩 요청했다. 모든 스프라이트에는 "32×32에서 읽히는 제한 팔레트 16비트 픽셀 아트, 단일 피사체, 글자·로고·워터마크·원작 유사성 없음"을 공통으로 지정했다.

### 질풍 무녀

```text
Joseon-inspired gale shaman heroine wearing a pale teal jeogori and deep indigo chima, a wind-tossed ribbon and one folded flame fan, neutral fixed-facing three-quarter top-down pose, flat #ff00ff chroma-key background.
```

### 보물상자

```text
Yellow chest: one closed dark lacquered wooden chest with brass-gold bands and a cloud-shaped clasp, flat #ff00ff background.
Purple chest: one closed black lacquered evolution chest with muted violet bands, a moon-shaped clasp and contained ghost-light, flat #00ff00 background.
```

### 추가 아이템

```text
Flame fan: one open charcoal folding fan with three contained orange-red flame shapes, flat #00ff00 background.
Warding sword: one short straight iron blade with a dark grip and pale-gold knot, flat #00ff00 background.
Returning boomerang: one crescent carved dark-wood projectile with ivory edge marks and a muted red cord, flat #00ff00 background.
Thunder bell: one aged bronze hand bell with a cloud crown and contained pale-blue lightning spark, flat #ff00ff background.
Spirit gourd: one dark ceramic gourd flask with an ivory cord and contained pale-cyan ghost flame, flat #ff00ff background.
```

### 달빛 대나무 로비

```text
Wide 16:9 original Joseon dark-fantasy ruined courtyard framed by black bamboo and silver grass, a large pale full moon behind mist, broken stone lanterns, central lower negative space for JavaFX controls, crisp limited-palette 16-bit pixel art, no characters or text.
```

## 오디오 자산

| 자산 | 파일 | 제작과 검수 |
| --- | --- | --- |
| 달빛 폐허 BGM | `desktop-app/src/main/resources/assets/audio/bgm-moonlit-ruins.wav` | 12초, 22.05kHz mono PCM. 110·165·220Hz 화음과 느린 진폭 변조를 코드로 합성 |
| 레벨업 효과 | `desktop-app/src/main/resources/assets/audio/sfx-level-up.wav` | 0.8초 상승 음정 합성 |
| 호신결계 효과 | `desktop-app/src/main/resources/assets/audio/sfx-guard.wav` | 0.45초 하강 공명음 합성 |
| 상자 효과 | `desktop-app/src/main/resources/assets/audio/sfx-chest.wav` | 0.9초 삼화음 합성 |
| 패배 효과 | `desktop-app/src/main/resources/assets/audio/sfx-defeat.wav` | 0.8초 하강 화음 합성 |

오디오는 외부 음원을 사용하지 않고 Python 표준 `wave`와 수학 함수로 직접 합성했다. 모든 파일은 16비트 mono PCM WAV이며 프로젝트 MIT 라이선스 범위에 포함한다.

## 공통 검수 결과

1. 기존 다섯 파일과 추가 스프라이트 여덟 파일, 총 13개 스프라이트의 실제 크기 32×32를 확인했다.
2. 모든 전경 자산은 RGBA와 알파 범위 0–255, 네 모서리 알파 0을 확인했다.
3. 바닥은 8×8로 반복한 미리보기에서 두드러지는 이음선을 찾지 못했다.
4. 최근접 확대에서 흐림 없이 사냥꾼, 도깨비, 부적, 혼불이 즉시 구분된다.
5. 원작 이미지나 참조 이미지를 입력하지 않았고 독자적인 조선 야행 팔레트와 실루엣을 사용했다.

## 제작 이력

| 날짜 | 제작 도구 | 변경 내용 |
| --- | --- | --- |
| 2026-08-05 | Codex 내장 `image_gen`, `remove_chroma_key.py`, Pillow 12.2.0 | 최초 5개 자산 생성, 투명화, 32×32 변환과 검수 |
| 2026-08-05 | Codex 내장 `image_gen`, `remove_chroma_key.py`, Pillow 12.2.0, Python `wave` | 질풍 무녀·상자·아이템·로비 배경과 자체 합성 오디오 추가 |
| 2026-08-05 | Google 공식 Sign in assets | 승인된 Google G 아이콘을 로그인 버튼 자산으로 추가 |
