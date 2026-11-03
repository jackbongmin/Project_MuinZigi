# 03일차 · 문서 보완, Jev 프레임 판단, 재시도와 원문 로그

- 날짜: 2026-10-30
- 단계: 1단계 (가이드 03, 04)
- 오전에는 계획 문서를 보완했고, 이어서 가이드 03(Jev 프레임 판단)과 가이드 04(Jev 재시도와 원문 로그)를 진행함. 개념 설명은 가이드 03·04와 결정 004·005에 있음

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

- 가이드 04 완료: Jev 호출 재시도와 시도 기록 (`feature/jev-retry-log`)
  - 408·429·5xx·시간 초과는 최대 2번 다시 보냄 (0.5초 → 1초, 지터 25%). 400처럼 요청이 잘못된 경우는 다시 보내지 않음
  - 시도마다 상태 코드·요청·응답 원문·걸린 시간·보낸 시각을 `jev_call_logs`에 남김. 이미지 base64(약 200KB)는 크기만 남겨 한 줄 약 1KB
  - 재시도까지 실패하면 500 대신 502. 다시 부르면 실패한 프레임부터 이어서 함 (가이드 03의 건너뛰기 덕분)
  - 테스트 22개 통과 (새 테스트 5개). 테스트 코드 2개는 Claude Code가 가이드 코드로 채움
  - 커버리지: 줄 84.7% → 93.1%, 분기 83.3% → 86.2%. 기본 테스트에서 8줄만 돌던 `JevClient`가 62/67줄로 올라감
  - 재시도는 Spring `@Retryable` 대신 반복문으로 직접 만들고, 기록은 DB에 남기기로 함 → 결정 005
- 가이드 04를 만들면서 실제 서버에서 찾은 문제: 본문을 읽다 난 시간 초과가 재시도되지 않고 500이 남
  - 가짜 서버 테스트는 통과했음. 응답 제한을 1ms로 줄여 실제로 시간 초과를 만들어 보고서야 드러남
  - 본문 읽기 중 시간 초과는 `ResourceAccessException`이 아닌 `RestClientException`으로 나와 `catch`에 안 걸렸음 → `exchange()`로 바꿔 해결
  - 자세한 과정: `docs/portfolio/troubleshooting/2026-10-30_본문_읽기_시간_초과가_재시도되지_않음.md`
- 어노테이션 정리 문서를 만듦: `docs/study/어노테이션_정리.md` (지금까지 나온 어노테이션 33개를 누가 읽는지 기준으로 나눔)

## N+1 문제를 어떻게 해결했나
- **문제**: 판단 목록 응답에는 프레임 시각이 필요함. 그런데 `FrameJudgment`의 프레임은 `LAZY`라서, 판단 결과 N개를 읽은 뒤 `getFrame().getTimeSec()`을 부를 때마다 프레임을 1개씩 따로 읽음 → 쿼리 1 + N번
- **헷갈린 점**: 조회 조건에 `j.frame.video.id`가 있어 SQL에 이미 `join`이 들어감. 하지만 `join`은 조건과 정렬에만 쓰이고 프레임 값은 가져오지 않음
- **해결**: `FrameJudgmentRepository.findAllWithFrame`에 `join fetch j.frame f`를 씀. 프레임 칸까지 한 번에 `select`해서 채움
- **측정**: 판단 5개 조회 시 fetch join 없이 6번 → fetch join으로 1번 (Hibernate 통계로 셈, P1 16장이면 17번 → 1번)
- **주의**: 같은 트랜잭션에서 방금 저장한 프레임은 1차 캐시에 있어서 추가 쿼리가 안 나감. 측정할 때 `em.clear()`로 비워야 N+1이 보임
- EAGER로 바꾸는 것은 답이 아님. JPQL 조회에서는 EAGER도 따로 읽어서 N+1이 남고, 프레임이 필요 없는 조회에서도 항상 읽게 됨
- 자세한 과정: `docs/portfolio/troubleshooting/2026-10-30_판단_목록_조회_N+1.md`

## mock 정리: 무엇이고, 언제, 왜, 어떻게 쓰나
### 무엇인가
- **mock(가짜 객체)**: 진짜 객체 대신 넣는, **정해 둔 대로만 답하는 가짜**임
- 진짜 `JevClient`는 인터넷으로 Jev를 부름. mock `JevClient`는 아무 데도 안 가고 `when(...).thenReturn(...)`으로 정해 둔 값을 바로 돌려줌
- 비슷한 말로 **가짜 서버**(`MockRestServiceServer`)가 있음. 객체가 아니라 **HTTP 서버만** 가짜로 바꿈

### 언제 쓰나
테스트하려는 코드가 **내 손으로 통제할 수 없는 것**에 기대고 있을 때 씀.

| 이런 것에 기대면 | 문제 | 우리 프로젝트 |
|---|---|---|
| 돈이 드는 외부 API | 테스트를 돌릴 때마다 비용이 나감 | Jev 호출 (프레임당 약 0.0001달러) |
| 인터넷·외부 서버 상태 | 서버가 느리거나 꺼지면 내 코드가 맞아도 테스트가 실패함 | Jev 서버 |
| 답이 매번 다른 것 | 같은 입력에도 결과가 달라 "맞다"를 확인할 수 없음 | Jev 확률 (0.28이 다음엔 0.26) |
| 일부러 만들기 어려운 실패 | 429·529·시간 초과를 진짜로 만들 수 없음 | 재시도 테스트 |

- 반대로 **DB는 mock으로 바꾸지 않음.** Docker PostgreSQL을 진짜로 씀. 쿼리(fetch join, jsonb 저장)가 실제로 맞게 도는지가 확인 대상이라서임

### 왜 쓰나 (무엇을 확인하나)
- mock을 쓰면 확인하는 대상이 바뀜. **"Jev가 맞게 판단하나"가 아니라 "우리 코드가 Jev의 답을 받아 맞게 처리하나"**를 확인함
- 그래서 mock 테스트가 통과해도 확인 못 하는 것이 남음
  - Jev 판단의 정확도 → `jevTest`(실제 호출)로 따로 잼
  - mock으로 흉내 내지 않은 실패 → 오늘 본문 읽기 시간 초과처럼, **실제 서버에서 일부러 실패를 만들어** 확인함

### 어떻게 쓰나: 두 가지 방법
| | `@MockitoBean` (가이드 03) | `MockRestServiceServer` (가이드 04) |
|---|---|---|
| 가짜로 바꾸는 것 | `JevClient` **객체 전체** | Jev **HTTP 서버**만 |
| 실행되는 우리 코드 | 서비스·컨트롤러 (`JevClient`는 안 돎) | `JevClient`의 요청 만들기·재시도·응답 읽기 |
| 확인하는 것 | 판단 결과·시도 기록을 맞게 저장하나, 502를 돌려주나 | 요청 본문이 맞나, 어떤 실패를 몇 번 재시도하나 |
| 테스트 | `JudgmentControllerTest` | `JevClientTest` |

**`@MockitoBean` 쓰는 법** (Spring을 띄우는 테스트에서)
```java
@MockitoBean
JevClient jevClient;                                   // Spring이 진짜 JevClient 대신 이 가짜를 넣음

when(jevClient.askYesNo(any(byte[].class), eq("jpg"), eq(FrameQuestions.QUESTIONS)))
        .thenReturn(fakeResult);                       // 이렇게 부르면 이 값을 돌려줘라
        // .thenThrow(new JevCallException(...))       // 또는 이 예외를 던져라

verify(jevClient, times(5)).askYesNo(...);             // 정말 5번 불렸는지 확인
```
- `any(...)`: 어떤 값이 와도 됨. `eq(...)`: 정확히 이 값이어야 함. 조건이 안 맞으면 가짜는 `null`을 돌려줌

**`MockRestServiceServer` 쓰는 법** (Spring 없이)
```java
RestClient.Builder builder = RestClient.builder().baseUrl("https://jev.test");
server = MockRestServiceServer.bindTo(builder).build();      // 이 builder로 만든 클라이언트의 요청을 가짜 서버가 받음
jevClient = new JevClient(builder.build(), ...);

server.expect(requestTo(URL))                                // 이런 요청이 와야 하고
        .andExpect(jsonPath("$.model").value("jev-1.14.0"))
        .andRespond(withStatus(HttpStatusCode.valueOf(529))); // 이렇게 답함
server.verify();                                             // 기대한 요청이 다 왔는지
```
- 이 방법을 쓰려면 **바꿔 낄 수 있게 코드를 나눠야** 함. 그래서 `RestClient`를 `JevClient` 안에서 만들지 않고 `JevConfig`에서 만들어 넘기게 바꿈

### 주의할 점
- **mock은 내가 상상한 상황만 흉내 냄.** `withException(...)`은 "요청 직후 실패"만 흉내 내서, "본문을 읽다 실패"는 놓쳤음. mock 테스트가 통과해도 실제 환경에서 한 번은 확인함
- **모든 걸 mock으로 바꾸면 아무것도 확인하지 않는 테스트가 됨.** 가짜가 돌려준 값을 그대로 확인하면 의미가 없음. mock은 테스트 대상 **바깥**(외부 API)에만 씀
- 가이드 03의 `JudgmentControllerTest`는 mock `JevClient` + 진짜 DB, 가이드 04의 `JevClientTest`는 진짜 `JevClient` + 가짜 HTTP 서버임. 둘을 합쳐야 "요청 → 재시도 → 저장" 전체가 확인됨

## 리뷰에서 지적된 점
- 동작과 구조는 가이드와 같음. 지적은 주석 오타와 쓰지 않는 import 정도라 기능에 영향이 없어 그대로 둠
  - 주석 오타 2곳 (`FrameQuestions`, `JudgmentService`), 쓰지 않는 `import java.util.Collection;`, `FrameService` 주석의 `1단계 · 03 (imagePath 추가)` 줄 빠짐
- 가이드 04: 기능 코드 8개는 가이드 코드와 같음 (주석·빈 줄 차이만 있음). 테스트 2개(`JevClientTest`, `JudgmentControllerTest`)는 Claude Code가 가이드 코드로 채움

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
- 가이드 04: `docs/guide/04_Jev_재시도와_원문_로그.md`, 결정 005, 트러블슈팅(본문 읽기 시간 초과), `application.properties`(시간 제한·재시도 설정)
  - 새 파일: `JevConfig`, `JevCallAttempt`, `JevResult`, `JevCallException`, `JevCallLog`, `JevCallLogRepository`, `JevClientTest`
  - 바뀐 파일: `JevClient`, `JudgmentService`, `JudgmentControllerTest`, 0단계 실제 호출 테스트 4개(반환 타입 변경)
- `docs/study/어노테이션_정리.md`: 어노테이션 정리

## 오늘 배운 것
- **문서에 쓰는 단어도 정의해야 함.** "이벤트"처럼 자주 쓰던 단어도 막상 정의하려니 "아무것도 안 가져간 방문도 정상 이벤트인가?"를 정해야 했음. 이것을 정하지 않으면 오탐률 숫자가 측정할 때마다 달라짐
- **실제 서버에서도 P1의 넣는 동작은 0.28에 그침.** 뒤에서 찍혀 손이 가려진 장면은 행동 판단으로 못 잡는다는 결정 001이 기능 코드에서도 그대로 확인됨
- **가짜 서버 테스트가 통과해도 실제 환경에서는 다를 수 있음.** 설정값(응답 제한 1ms)만 바꿔서 일부러 실패를 만들어 보니, 테스트가 흉내 내지 못한 실패가 드러남
- **브랜치 두 개가 같은 줄 근처를 고치면 충돌이 남.** 문서 규칙처럼 여러 브랜치가 같이 고치는 파일은 한쪽을 먼저 합친 뒤 다음 브랜치를 시작하는 편이 편함

## AI 결과를 의심하고 확인한 것
- 아직 없음

## 스스로 답해볼 질문
1. Jev 호출을 `@Transactional` 메서드 안에서 하면 어떤 문제가 생기는가 (면접 질문)
2. 판단 결과를 질문별 칸이 아니라 `jsonb` 한 칸에 저장한 이유와 단점은 무엇인가
3. fetch join 없이 판단 목록을 조회하면 쿼리가 몇 번 나가는가 (N+1, 면접 질문)
4. 같은 요청을 두 번 보내도 결과가 중복되지 않는 성질(멱등성)이 재시도에 왜 중요한가
5. 어떤 실패는 재시도하고 어떤 실패는 하지 않는가. 지수 백오프에 지터를 더하는 이유는 무엇인가 (면접 질문)
6. mock 테스트가 확인하는 것과 확인하지 못하는 것은 무엇인가. 오늘 가짜 서버 테스트가 놓친 실패는 무엇이었나
