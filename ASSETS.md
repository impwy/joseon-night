# 조선 야행 자산 제작 기록

## 사용 원칙

- 다른 게임의 이미지, 스프라이트, 음악, 로고를 입력 또는 최종 자산으로 사용하지 않는다.
- 텍스트 프롬프트에서 시작한 독자적인 조선 다크 판타지 시안만 사용한다.
- 스프라이트는 후처리와 검수를 마친 최종 32×32 PNG로, 로비 배경은 1280×720 PNG로 저장한다.
- 캐릭터, 적, 투사체, 혼불과 배경 장식은 투명 RGBA, 바닥은 불투명 RGB로 저장한다.
- 화면에서는 최근접 보간을 사용하며, 코드와 프로젝트에서 직접 만든 자산에는 MIT License를 적용한다. Google 브랜드 자산은 별도 사용 조건을 따른다.

## 최종 자산

| 자산 | 최종 파일 | 후처리와 검수 | 상태 |
| --- | --- | --- | --- |
| 도깨비 사냥꾼 | `desktop-app/src/main/resources/assets/sprites/player.png` | 마젠타 제거, 30px 안에 비율 유지, 32×32 RGBA. 네 모서리 알파 0과 작은 화면 실루엣 확인 | 완료 |
| 그림자 도깨비 | `desktop-app/src/main/resources/assets/sprites/enemy.png` | 마젠타 제거, 30px 안에 비율 유지, 32×32 RGBA. 플레이어와 색·실루엣 구분 확인 | 완료 |
| 도깨비 장수 | `desktop-app/src/main/resources/assets/sprites/dokkaebi-warlord.png` | 초록색 제거, JDK ImageIO 최근접 축소로 30px 안에 비율 유지, 32×32 RGBA. 투명 모서리와 기존 적과 다른 픽셀 확인 | 완료 |
| 봉인 부적 | `desktop-app/src/main/resources/assets/sprites/talisman.png` | 마젠타 제거, 26px 안에 비율 유지, 32×32 RGBA. 축소 후 직사각형과 금색 테두리 확인 | 완료 |
| 혼불 | `desktop-app/src/main/resources/assets/sprites/soul-flame.png` | 마젠타 제거, 22px 안에 비율 유지, 32×32 RGBA. 바닥과 투사체에서 청백색 불꽃 식별 확인 | 완료 |
| 달빛 폐허 바닥 | `desktop-app/src/main/resources/assets/sprites/ground.png` | 전체 원본을 최근접 보간으로 32×32 RGB 변환. 8×8 반복 미리보기에서 고대비 경계 없음 확인 | 완료 |
| 마른 풀 장식 | `desktop-app/src/main/resources/assets/sprites/decoration-dry-grass.png` | 마젠타 제거, 32×32 RGBA. 네 모서리 투명도와 작은 화면의 황갈색 풀 실루엣 확인 | 완료 |
| 잔돌·깨진 기와 장식 | `desktop-app/src/main/resources/assets/sprites/decoration-rubble.png` | 초록색 제거, 32×32 RGBA. 돌과 전통 기와 조각의 청회색 실루엣 확인 | 완료 |
| 얕은 균열 장식 | `desktop-app/src/main/resources/assets/sprites/decoration-ground-crack.png` | 초록색 제거, 32×32 RGBA. 깊은 구덩이로 보이지 않는 낮은 대비의 갈라짐 확인 | 완료 |
| 질풍 무녀 | `desktop-app/src/main/resources/assets/sprites/gale-maiden.png` | 마젠타 제거, 30px 안에 비율 유지, 32×32 RGBA와 투명 모서리 확인 | 완료 |
| 노란 보물상자 | `desktop-app/src/main/resources/assets/sprites/chest-yellow.png` | 마젠타 제거, 30px 안에 비율 유지, 금색 실루엣 확인 | 완료 |
| 보라 보물상자 | `desktop-app/src/main/resources/assets/sprites/chest-purple.png` | 초록색 제거, 30px 안에 비율 유지, 노란 상자와 색·잠금 장식 구분 확인 | 완료 |
| 화염 부채 | `desktop-app/src/main/resources/assets/sprites/flame-fan.png` | 초록색 제거, 28px 안에 비율 유지, 불꽃 세 갈래 확인 | 완료 |
| 벽사검 | `desktop-app/src/main/resources/assets/sprites/warding-sword.png` | 초록색 제거, 28px 안에 비율 유지, 대각선 검 실루엣 확인 | 완료 |
| 회귀 부메랑 | `desktop-app/src/main/resources/assets/sprites/returning-boomerang.png` | 초록색 제거, 28px 안에 비율 유지, 초승달형 실루엣 확인 | 완료 |
| 낙뢰 방울 | `desktop-app/src/main/resources/assets/sprites/thunder-bell.png` | 마젠타 제거, 28px 안에 비율 유지, 청백색 낙뢰 강조 확인 | 완료 |
| 혼령 호리병 | `desktop-app/src/main/resources/assets/sprites/spirit-gourd.png` | 마젠타 제거, 28px 안에 비율 유지, 혼불과 호리병 구분 확인 | 완료 |
| 달빛 대나무 로비 | `desktop-app/src/main/resources/assets/backgrounds/lobby-moonlit-courtyard.png` | 원본을 최근접 방식으로 1280×720 RGB 변환, 대나무·달·풀·안개와 중앙 UI 여백 확인 | 완료 |

기존 투명 자산은 내장 `image_gen`으로 단색 배경 원본을 만든 뒤 설치된 `remove_chroma_key.py`의 border 자동 추출, soft matte, despill과 Pillow 12.2.0의 최근접 축소를 적용했다. 배경 장식 3종은 같은 투명화 절차 뒤 macOS `sips`로 중심을 잘라 32×32로 축소했고, JDK `ImageIO` 테스트로 크기·알파 채널·네 투명 모서리를 자동 검사한다.

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

### 도깨비 장수

```text
Use case: stylized-concept
Asset type: original 2D game enemy sprite
Primary request: create exactly one Joseon dark-fantasy dokkaebi warlord for a top-down survival game, an imposing non-human stage-two enemy with a broad armored silhouette
Scene/backdrop: perfectly flat solid #00ff00 chroma-key background for local removal
Subject: exactly one full-body non-human dokkaebi warlord, broad shoulders, two uneven short horns, dark rust-red lamellar armor, charcoal spirit body, small pale cyan ghost-fire eyes, one compact traditional helmet crest; no weapon and no separate object
Style/medium: authentic crisp 16-bit pixel art, limited palette, hard square pixel clusters, no smoothing, designed to remain distinct and readable when reduced to 32x32 pixels
Composition/framing: centered single sprite in a three-quarter top-down game view, full body visible, generous empty padding on every side
Lighting/mood: restrained cool moonlight, formidable but not graphic
Color palette: dark rust red, charcoal, blackened iron, muted brown, tiny pale cyan eye accents; do not use green in the subject
Constraints: background must be one perfectly uniform #00ff00 color with no shadows, gradients, texture, reflections, floor plane, or lighting variation; crisp separated silhouette; no cast shadow; no contact shadow; no glow outside the silhouette; no text; no watermark; no logo; no frame; no sprite sheet; no resemblance to any existing game character or enemy
Avoid: human face, slender human proportions, bright saturated red, excessive detail that disappears at 32x32, antialiased painting, multiple characters
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

## 배경 장식 자산 프롬프트

아래 장식은 참조 이미지 없이 내장 `image_gen`에 각각 한 번씩 요청했다. 공통으로 완전히 평평한 단색 크로마키 배경, 그림자·빛 번짐·글자·추가 물체 없음, 위에서 내려다본 단일 16비트 픽셀 아트 피사체, 32×32 축소 가독성과 넉넉한 여백을 지정했다.

### 마른 풀

```text
One isolated compact irregular tuft of sparse bent ochre, tan and muted-brown dry grass, direct top-down view, restrained dark moonlit palette, crisp hard pixel clusters, perfectly flat #ff00ff backdrop, no magenta in the subject.
```

### 잔돌·깨진 기와

```text
One compact harmless ground-debris cluster of small gray stones and two or three broken traditional dark roof-tile fragments, direct top-down view, charcoal and slate with cool moonlit highlights, crisp hard pixel clusters, perfectly flat #00ff00 backdrop, no green in the subject.
```

### 얕은 균열

```text
One shallow irregular branching ground crack with a few angular chips, direct top-down view, subtle charcoal, cool-gray and muted-brown pixels, flat rather than a deep pit, crisp hard pixel clusters, perfectly flat #00ff00 backdrop, no green in the subject.
```

## 오디오 자산

### 4차 음향 참고 자료

| 참고 자료 | 확인한 특성 | 라이선스와 적용 범위 |
| --- | --- | --- |
| [PorkMuncher의 `swoosh.wav`](https://freesound.org/people/PorkMuncher/sounds/263595/) | 짧은 검 휘두름에서 공기가 빠르게 갈라지는 질감 | CC0. 원본 파일이나 표본은 사용하지 않고 벽사검의 잡음 포락선 설계만 참고 |
| [f4ngy의 `Dealing Card`](https://freesound.org/people/f4ngy/sounds/240777/) | 짧고 마른 종이·카드 튕김 질감 | CC BY 4.0. 원본 파일이나 표본은 사용하지 않고 봉인 부적의 다중 임펄스 설계만 참고 |
| [공유마당 `국악 배경음악 #55`](https://gongu.copyright.or.kr/gongu/wrt/wrt/view.do?wrtSn=13379638&menuNo=200026) | 국악기 음색의 층, 저음과 장단이 함께 움직이는 전체 분위기 | 주식회사 아이티앤, CC BY. 로컬 참고 파일을 저장소에 넣지 않고 원곡 선율도 옮기지 않음 |

최종 WAV는 세 참고 자료를 변형한 파일이 아니다. 소리의 역할과 질감만 분석한 뒤 고정 seed, 수학 함수와 합성 잡음으로 새 표본을 처음부터 계산했다.

| 자산 | 파일 | 제작과 검수 |
| --- | --- | --- |
| 로비 BGM | `desktop-app/src/main/resources/assets/audio/bgm-lobby.wav` | 36초, 독자 오음계 48박 프레이즈·저음 진행·피리 3배음·뜯는 현 답구·성긴 장단과 바람을 합성. 0.75초 원형 크로스페이드와 반복 경계의 표본·에너지를 자동 검사 |
| 전투 BGM | `desktop-app/src/main/resources/assets/audio/bgm-combat.wav` | 48초, 독자 오음계 96박 프레이즈·움직이는 저음·피리·현악 답구와 16단계 장단풍 타격을 합성. 0.75초 원형 크로스페이드와 반복 경계의 표본·에너지를 자동 검사 |
| 기본 아이템 공격 6종 | `sfx-attack-seal-talisman.wav`, `sfx-attack-flame-fan.wav`, `sfx-attack-exorcist-sword.wav`, `sfx-attack-returning-boomerang.wav`, `sfx-attack-thunder-bell.wav`, `sfx-attack-spirit-gourd.wav` | 부적의 종이 플릭, 벽사검의 공기 가르기·금속 여운, 낙뢰의 초기 크랙·저역 충격처럼 공격 역할별 합성기를 분리 |
| 진화 아이템 공격 6종 | `sfx-attack-ten-thousand-seal-array.wav`, `sfx-attack-heavenly-thunder-seal.wav`, `sfx-attack-inferno-returning-wheel.wav`, `sfx-attack-blue-flame-spirit-gourd.wav`, `sfx-attack-lunar-eclipse-twin-blades.wav`, `sfx-attack-thunder-flame-divine-orb.wav` | 기본 질감을 확장하되 검은 이중 휘두름, 낙뢰는 추가 갈래 크랙 등 서로 다른 포락선·배음·좌우 위치로 합성 |
| 레벨업 효과 | `desktop-app/src/main/resources/assets/audio/sfx-level-up.wav` | 0.8초 상승 음정 합성 |
| 호신결계 효과 | `desktop-app/src/main/resources/assets/audio/sfx-guard.wav` | 0.45초 하강 공명음 합성 |
| 상자 효과 | `desktop-app/src/main/resources/assets/audio/sfx-chest.wav` | 0.9초 삼화음 합성 |
| 패배 효과 | `desktop-app/src/main/resources/assets/audio/sfx-defeat.wav` | 0.8초 하강 화음 합성 |
| 이전 달빛 폐허 BGM | `desktop-app/src/main/resources/assets/audio/bgm-moonlit-ruins.wav` | 2차 구현 호환 기록으로 보존하지만 현재 장면 재생에는 사용하지 않음 |

새 BGM과 아이템 효과음은 외부 음원이나 샘플을 사용하지 않고 `tools/generate_audio_assets.py`의 Python 표준 `wave`와 수학 함수로 직접 합성했다. 모두 22.05kHz, 16비트, 스테레오 PCM WAV이며 프로젝트 MIT 라이선스 범위에 포함한다. 고정 seed의 자체 난수 생성기를 사용해 같은 소스에서 항상 같은 파일이 만들어진다.

```bash
python3 tools/generate_audio_assets.py
python3 tools/generate_audio_assets.py --check-only
```

첫 명령은 2개 BGM과 12개 공격 효과음을 재생성한 뒤 검수하고, 두 번째 명령은 파일을 바꾸지 않고 표본률·채널·비트 깊이·길이·무음·클리핑과 BGM 반복 경계를 검사한다. Java 테스트는 모든 `SoundCue`가 실제 파일과 연결되는지도 확인한다.

## 공통 검수 결과

1. 기존 스프라이트 13개, 도깨비 장수와 배경 장식 3개, 총 17개 스프라이트의 실제 크기 32×32를 확인했다.
2. 모든 전경 자산은 RGBA와 알파 범위 0–255, 네 모서리 알파 0을 확인했다.
3. 바닥은 8×8로 반복한 미리보기에서 두드러지는 이음선을 찾지 못했다.
4. 이미지 스무딩을 끈 64픽셀 확대에서 사냥꾼, 그림자 도깨비, 적갈색 갑주의 도깨비 장수, 부적, 혼불과 장식 3종이 즉시 구분된다.
5. 원작 이미지나 참조 이미지를 입력하지 않았고 독자적인 조선 야행 팔레트와 실루엣을 사용했다.
6. 새 오디오 14개는 22.05kHz, 16비트 스테레오 PCM이며 무음·클리핑·잘린 표본이 없고 BGM 반복 경계를 통과했다.
7. 오디오를 재생성하기 전과 후의 SHA-256이 같아 제작 결과가 재현됨을 확인했다.

## 제작 이력

| 날짜 | 제작 도구 | 변경 내용 |
| --- | --- | --- |
| 2026-08-05 | Codex 내장 `image_gen`, `remove_chroma_key.py`, Pillow 12.2.0 | 최초 5개 자산 생성, 투명화, 32×32 변환과 검수 |
| 2026-08-05 | Codex 내장 `image_gen`, `remove_chroma_key.py`, Pillow 12.2.0, Python `wave` | 질풍 무녀·상자·아이템·로비 배경과 자체 합성 오디오 추가 |
| 2026-08-05 | Google 공식 Sign in assets | 승인된 Google G 아이콘을 로그인 버튼 자산으로 추가 |
| 2026-08-05 | `tools/generate_audio_assets.py`, Python 표준 `wave` | 36초 로비·48초 전투 BGM과 기본·진화 아이템 공격 효과음 12개를 결정적으로 합성하고 자동 검수 추가 |
| 2026-08-06 | `tools/generate_audio_assets.py`, Python 표준 `wave` | 외부 표본·선율 없이 다중 오음계 프레이즈 BGM, 종이 플릭·검 휘두름·직접 낙뢰 중심의 역할별 합성기로 14개 음원 재설계 |
| 2026-08-06 | Codex 내장 `image_gen`, `remove_chroma_key.py`, macOS `sips`, JDK `ImageIO` | 마른 풀·잔돌/깨진 기와·얕은 균열 장식 3종을 생성하고 32×32 투명 PNG 검증 추가 |
| 2026-08-06 | Codex 내장 `image_gen`, `remove_chroma_key.py`, JDK `ImageIO` | 도깨비 장수를 초록 크로마키로 생성하고 투명화한 뒤 최근접 방식으로 32×32 축소·검증 |
