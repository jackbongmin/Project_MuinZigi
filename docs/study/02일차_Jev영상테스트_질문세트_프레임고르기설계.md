# 02일차 · Jev 영상 테스트, 질문 세트 실험, 프레임 고르기 설계

- 날짜: 2026-10-29
- 단계: 0단계 마무리 → 1단계 (가이드 02 작성)
- 오늘은 기능 코드 없이 테스트·실험과 설계만 진행함. 개념 설명은 가이드 02와 결정 기록에 있음

## 오늘 한 것
- AI 생성 영상 7편(P2·P3·N1~N5)을 1초에 1장씩 뽑아 Jev에 보냄 (P1 포함 109장)
  - 정답을 정해 채점함: 약 95% 일치. 자기 폰을 가방에 넣는 장면(N5)을 "넣음"으로 오탐함
  - 결과: `docs/test-data/2026-10-29_jev_영상_테스트.md`
- 질문 세트 v1·v2: "개인 물건은 제외" 문장으로 자기 물건 오탐 7개 → 0개. 대신 진짜 넣는 장면도 0.5 근처로 내려감
  - 기록: `docs/portfolio/performance/2026-10-29_질문_문구_수정으로_자기물건_오탐_감소.md`
- 내 의문 "질문을 Y/N으로 분기하고 더 세분화하면 도난을 잘 잡지 않을까?"를 실험함
  - 분기: 이미지를 매번 다시 보내서 토큰 약 81% 증가 (추정). 질문 하나는 약 27토큰, 이미지가 약 2,700토큰
  - 세분화(넣은 곳 질문, v3): 같은 장면을 오히려 더 못 잡음
  - 진열대 전후 비교: 행동 판단으로 못 가른 장면(P2, P3, N4)을 모두 맞힘
  - 기록: `docs/portfolio/decisions/002_질문_분기_대신_한번에_묻고_진열대_비교로_판정.md`
- 진열대 칸만 잘라 보내기: 토큰 -70%, 정답 수는 같음
  - 기록: `docs/portfolio/performance/2026-10-29_진열대_칸_자르기로_토큰_감소.md`
- 0단계 완료 처리. 1단계 질문 세트는 v2로 시작함
- 프레임 고르기 설계: "변화가 큰 프레임만" 보내면 호출은 절반이 되지만 몰래 넣는 동작을 버림 → "움직임 앞뒤 2초를 통째로" 보내기로 함
  - 기록: `docs/portfolio/decisions/003_장면_변화_필터_대신_활동_구간_전체를_보냄.md`
  - 가이드: `docs/guide/02_프레임_추출과_고르기.md` (Claude Code가 먼저 작성해 테스트 13개 통과를 확인하고, 기능 코드는 지워 둠)

- 브랜치 나눔: `main` → `develop` → `feature/frame-extraction` (규칙은 CLAUDE.md "브랜치 규칙")
- 가이드 02 코드 작성 (테스트 코드는 이번만 Claude Code가 넣음)
  - 테스트 13개 통과, 실제 서버에서 P1 업로드 → 16장(0~3초, 8~19초), 같은 영상 다시 추출하면 409 확인

## 리뷰에서 지적된 점
- `FrameExtractor`에 `@Component`를 빠뜨림 → Spring이 빈을 못 찾아(`No qualifying bean of type 'FrameExtractor'`) 서버가 안 뜨고, 상관없는 기존 테스트까지 8개가 실패함
  - `new`로 직접 만드는 `FrameExtractorTest`는 통과함. 단위 테스트만으로는 못 잡고 통합 테스트(Spring을 띄우는 테스트)가 잡는 실수임
  - import만 있고 쓰이지 않으면 IntelliJ가 회색으로 표시함. 회색 import는 빠뜨린 것이 없는지 의심해 볼 것
- `FrameSelector` 주석 오타: "움직밍" → "움직임", "노려서" → "놓쳐서" (뜻이 반대가 됨)

## 바뀐 파일
- `backend/src/main/java/com/mujinjigi/domain/video/`: `ExtractedFrame`, `FrameExtractor`, `FrameSelector`, `FrameService`, `VideoFrame`, `VideoFrameRepository`, `VideoFrameResponse`, `VideoFrameController` 추가, `VideoFileStorage.pathOf()` 추가
- `backend/src/test/java/com/mujinjigi/domain/video/`: `TestVideos`, `FrameSelectorTest`, `FrameExtractorTest`, `VideoFrameControllerTest`
- `backend/src/test/java/com/mujinjigi/domain/analysis/jev/QuestionSetV1~V3.java`: 질문 세트 실험
- `backend/src/test/java/com/mujinjigi/domain/analysis/jev/JevFrameLiveTest.java`: 여러 질문 세트를 같은 프레임에 연달아 보냄
- `backend/src/test/java/com/mujinjigi/domain/analysis/jev/JevShelfLiveTest.java`: 장면별 화면 전체 / 칸 자르기 비교 추가
- `backend/src/main/resources/application.properties`: 프레임 폴더, FFmpeg 경로, 프레임 고르기 기준값 (가이드 02 "미리 바꿔둔 설정")
- `docs/handoff.md`: 0단계 완료 표시

## 오늘 배운 것
- **정답은 결과를 보기 전에 정함.** 오늘 첫 채점은 Jev 결과를 먼저 본 뒤 정답을 정해서 규칙을 어겼음. 이후 실험(v3, 진열대 비교)은 호출 전에 정답을 적어 두고 진행함
- **같은 입력도 결과가 매번 조금 다름.** v0을 세 번 돌리니 일치 수가 307, 308, 309개였음. 1~2개 차이를 개선이라고 말하면 안 됨
- **비용은 이미지 장수에 비례함.** 질문을 아끼는 것보다 보내는 프레임을 줄이는 것이 훨씬 효과가 큼
- **숫자가 좋아 보이는 설정이 목표에 맞는 설정은 아님.** 프레임 50% 감소 설정은 가장 중요한 순간을 골라서 버렸음

## 스스로 답해볼 질문
1. 질문을 Y/N에 따라 분기하는 방식이 오히려 비용을 늘리는 이유는 무엇인가 (면접 질문)
2. 사진 한 장으로는 "상품을 훔침"과 "자기 물건을 넣음"을 구분하기 어려운 이유는 무엇이고, 무엇으로 보완했는가
3. 같은 설정을 다시 돌렸을 때 결과가 달라진다면, 개선 효과를 어떻게 말해야 하는가
4. 장면 변화가 큰 프레임만 보내면 비용이 절반이 되는데 왜 그 방법을 쓰지 않았는가 (면접 질문)
5. 진열대 전후 비교의 약점은 무엇인가 (N5에서 실제로 나타난 문제)
