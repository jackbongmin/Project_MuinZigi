# 03일차 · 문서 보완, Jev 프레임 판단

- 날짜: 2026-10-30
- 단계: 1단계 (가이드 03 시작)
- 오전에는 계획 문서를 보완했고, 이어서 가이드 03(Jev 프레임 판단)을 준비함. 개념 설명은 가이드 03과 결정 004에 있음

## 오늘 한 것
- 계획 문서 보완: 네이버 D2 글 "AI 시대의 개발자를 꿈꾸는 학생 개발자에게"를 읽고 포트폴리오 관점에서 부족한 점을 찾음
  - 글의 핵심: 포트폴리오는 기술 나열보다 "왜 → 가설 → 어려움 → 해결 → 배운 것"의 이야기가 중요함. AI를 쓴 사실보다 AI 결과를 검증하고 고친 과정을 설명할 수 있어야 함
  - `handoff.md`에 반영한 것
    - 14장 용어 정의 추가. 오탐률 정의에 쓰이던 "이벤트"가 정의되지 않은 상태였음
    - 7장에 테스트 환경과 운영 환경의 차이, 운영 비용 추정(매장 하나 하루 약 1.7달러) 추가
    - 5장 2단계에 시각 맞추기, 금액은 `long`, 판정 로직을 순수 Java 클래스로 분리하는 규칙 추가
    - 9장 학습 노트 양식에 "AI 결과를 의심하고 확인한 것" 칸 추가
  - PR #1(`docs/portfolio-plan-update`)로 `develop`에 합침
- `feature/frame-extraction`을 PR #2로 `develop`에 합침. 두 브랜치가 `CLAUDE.md`의 같은 목록을 고쳐서 충돌이 났고, 양쪽 규칙을 모두 남겨 해결함
- `feature/jev-analysis` 브랜치를 만들고 가이드 03 작성
  - Claude Code가 먼저 코드를 작성해 테스트 17개 통과, 실제 Jev로 P1 16장 판단(약 4.6초, 장당 2,803토큰)을 확인한 뒤 기능 코드는 지우고 빈 파일만 남김
  - 판단 결과 저장 방식을 jsonb 한 칸으로 정함 → `docs/portfolio/decisions/004_판단_결과를_jsonb_한_칸에_저장.md`

- 테스트 커버리지 측정 시작 (JaCoCo)
  - 1·2일차 코드 기준선: 줄 78.6%, 분기 79.4%
  - 영상 조회 API(`GET /api/videos/{id}`)는 테스트가 하나도 없었음. `JevClient`의 요청 만들기는 실제 Jev 테스트에서만 실행되고 기본 테스트에서는 한 번도 안 돎
  - 전체 % 목표 대신 "판정 로직은 100%, 새 API의 정상 흐름은 빠짐없이"를 기준으로 둠 (handoff 8장)

- 가이드 03 완료: 프레임 판단 API(`POST`·`GET /api/videos/{videoId}/judgments`)
  - 테스트 17개 통과 (새 테스트 4개)
  - 실제 서버에서 P1 판단 16개, 4.7초, 입력 44,848토큰(장당 2,803). 조회 API는 0.03초
  - 같은 P1인데 16초 프레임의 `putting_in_bag`이 가이드 작성 때 0.28, 오늘 0.26으로 조금 다름. Jev 확률은 호출마다 조금씩 흔들림
  - 커버리지: 줄 76.6% → 84.7%, 분기 79.4% → 83.3%. 새 코드에서 빠진 줄은 `JudgmentService`의 이미지 읽기 실패(`catch`) 2줄뿐임

## N+1 문제를 어떻게 해결했나
- **문제**: 판단 목록 응답에는 프레임 시각이 필요함. 그런데 `FrameJudgment`의 프레임은 `LAZY`라서, 판단 결과 N개를 읽은 뒤 `getFrame().getTimeSec()`을 부를 때마다 프레임을 1개씩 따로 읽음 → 쿼리 1 + N번
- **헷갈린 점**: 조회 조건에 `j.frame.video.id`가 있어 SQL에 이미 `join`이 들어감. 하지만 `join`은 조건과 정렬에만 쓰이고 프레임 값은 가져오지 않음
- **해결**: `FrameJudgmentRepository.findAllWithFrame`에 `join fetch j.frame f`를 씀. 프레임 칸까지 한 번에 `select`해서 채움
- **측정**: 판단 5개 조회 시 fetch join 없이 6번 → fetch join으로 1번 (Hibernate 통계로 셈, P1 16장이면 17번 → 1번)
- **주의**: 같은 트랜잭션에서 방금 저장한 프레임은 1차 캐시에 있어서 추가 쿼리가 안 나감. 측정할 때 `em.clear()`로 비워야 N+1이 보임
- EAGER로 바꾸는 것은 답이 아님. JPQL 조회에서는 EAGER도 따로 읽어서 N+1이 남고, 프레임이 필요 없는 조회에서도 항상 읽게 됨
- 자세한 과정: `docs/portfolio/troubleshooting/2026-10-30_판단_목록_조회_N+1.md`

## 리뷰에서 지적된 점
- 동작과 구조는 가이드와 같음. 지적은 주석 오타와 쓰지 않는 import 정도라 기능에 영향이 없어 그대로 둠
  - 주석 오타 2곳 (`FrameQuestions`, `JudgmentService`), 쓰지 않는 `import java.util.Collection;`, `FrameService` 주석의 `1단계 · 03 (imagePath 추가)` 줄 빠짐

## 바뀐 파일
- `docs/handoff.md`: 용어 정의(14장), 운영 조건·비용(7장), 2단계 대조 규칙, 학습 노트 양식, 1단계 진행 상황
- `CLAUDE.md`: 학습 노트 질문은 사용자가 답함, 용어는 14장 기준
- `docs/guide/03_Jev_프레임_판단.md`: 가이드 03
- `docs/portfolio/decisions/004_판단_결과를_jsonb_한_칸에_저장.md`: 설계 결정
- `docs/portfolio/troubleshooting/2026-10-30_판단_목록_조회_N+1.md`: N+1 트러블슈팅
- `backend/build.gradle`: JaCoCo 커버리지 측정 추가
- `backend/src/main/java/com/mujinjigi/domain/analysis/`: `FrameQuestions`, `FrameJudgment`, `FrameJudgmentRepository`, `FrameJudgmentResponse`, `JudgmentService`, `JudgmentController`
- `backend/src/main/java/com/mujinjigi/domain/video/service/FrameService.java`: `imagePath()` 추가
- `backend/src/test/java/com/mujinjigi/domain/analysis/controller/JudgmentControllerTest.java`: 테스트 4개
- 모든 클래스 주석 첫 줄에 만든 단계·가이드 표시, 가이드 첫 줄에 한 줄 요약

## 오늘 배운 것
- **문서에 쓰는 단어도 정의해야 함.** "이벤트"처럼 자주 쓰던 단어도 막상 정의하려니 "아무것도 안 가져간 방문도 정상 이벤트인가?"를 정해야 했음. 이것을 정하지 않으면 오탐률 숫자가 측정할 때마다 달라짐
- **실제 서버에서도 P1의 넣는 동작은 0.28에 그침.** 뒤에서 찍혀 손이 가려진 장면은 행동 판단으로 못 잡는다는 결정 001이 기능 코드에서도 그대로 확인됨
- **브랜치 두 개가 같은 줄 근처를 고치면 충돌이 남.** 문서 규칙처럼 여러 브랜치가 같이 고치는 파일은 한쪽을 먼저 합친 뒤 다음 브랜치를 시작하는 편이 편함

## AI 결과를 의심하고 확인한 것
- 아직 없음

## 스스로 답해볼 질문
1. Jev 호출을 `@Transactional` 메서드 안에서 하면 어떤 문제가 생기는가 (면접 질문)
2. 판단 결과를 질문별 칸이 아니라 `jsonb` 한 칸에 저장한 이유와 단점은 무엇인가
3. fetch join 없이 판단 목록을 조회하면 쿼리가 몇 번 나가는가 (N+1, 면접 질문)
4. 같은 요청을 두 번 보내도 결과가 중복되지 않는 성질(멱등성)이 재시도에 왜 중요한가
5. 테스트 환경에서 통과한 판단 로직이 실제 매장 녹화본에서 틀릴 수 있는 이유를 세 가지 들어 보라
