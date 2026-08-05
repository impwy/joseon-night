# 조선 야행 자산 제작 기록

## 사용 원칙

- 다른 게임의 이미지, 스프라이트, 음악, 로고를 입력 또는 최종 자산으로 사용하지 않는다.
- 텍스트 프롬프트에서 시작한 독자적인 조선 다크 판타지 시안만 사용한다.
- 저장소에는 후처리와 검수를 마친 최종 32×32 PNG만 포함한다.
- 캐릭터, 적, 투사체와 혼불은 투명 RGBA, 바닥은 불투명 RGB로 저장한다.
- 화면에서는 최근접 보간을 사용하며, 코드와 자산 모두 MIT License를 적용한다.

## 최종 자산

| 자산 | 최종 파일 | 후처리와 검수 | 상태 |
| --- | --- | --- | --- |
| 도깨비 사냥꾼 | `desktop-app/src/main/resources/assets/sprites/player.png` | 마젠타 제거, 30px 안에 비율 유지, 32×32 RGBA. 네 모서리 알파 0과 작은 화면 실루엣 확인 | 완료 |
| 그림자 도깨비 | `desktop-app/src/main/resources/assets/sprites/enemy.png` | 마젠타 제거, 30px 안에 비율 유지, 32×32 RGBA. 플레이어와 색·실루엣 구분 확인 | 완료 |
| 봉인 부적 | `desktop-app/src/main/resources/assets/sprites/talisman.png` | 마젠타 제거, 26px 안에 비율 유지, 32×32 RGBA. 축소 후 직사각형과 금색 테두리 확인 | 완료 |
| 혼불 | `desktop-app/src/main/resources/assets/sprites/soul-flame.png` | 마젠타 제거, 22px 안에 비율 유지, 32×32 RGBA. 바닥과 투사체에서 청백색 불꽃 식별 확인 | 완료 |
| 달빛 폐허 바닥 | `desktop-app/src/main/resources/assets/sprites/ground.png` | 전체 원본을 최근접 보간으로 32×32 RGB 변환. 8×8 반복 미리보기에서 고대비 경계 없음 확인 | 완료 |

투명 자산은 내장 `image_gen`으로 단색 배경 원본을 만든 뒤 설치된 `remove_chroma_key.py`의 border 자동 추출, soft matte, despill을 적용했다. Pillow 12.2.0의 최근접 보간으로 축소했으며 네 투명 모서리, 알파 범위, 픽셀 크기를 자동 검사했다.

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

## 공통 검수 결과

1. 다섯 파일 모두 실제 크기 32×32를 확인했다.
2. 네 전경 자산은 RGBA와 알파 범위 0–255, 네 모서리 알파 0을 확인했다.
3. 바닥은 8×8로 반복한 미리보기에서 두드러지는 이음선을 찾지 못했다.
4. 최근접 확대에서 흐림 없이 사냥꾼, 도깨비, 부적, 혼불이 즉시 구분된다.
5. 원작 이미지나 참조 이미지를 입력하지 않았고 독자적인 조선 야행 팔레트와 실루엣을 사용했다.

## 제작 이력

| 날짜 | 제작 도구 | 변경 내용 |
| --- | --- | --- |
| 2026-08-05 | Codex 내장 `image_gen`, `remove_chroma_key.py`, Pillow 12.2.0 | 최초 5개 자산 생성, 투명화, 32×32 변환과 검수 |
