# 가이드 03 · Jev 프레임 판단

> **한 줄 요약**: 가이드 02에서 고른 **프레임 이미지**를 **Jev**(`JevClient`)에 보내 질문 4개에 "예"일 확률을 받고, 그 결과를 프레임과 연결해 **DB(`frame_judgments`)**에 저장하는 기능 구현임

## 목표
- 가이드 02에서 저장한 프레임을 한 장씩 Jev에 보내고, 질문마다 "예"일 확률을 DB에 저장함
- 0단계 실험에서 고른 질문 세트 v2를 테스트 코드에서 기능 코드로 옮김
- 결과마다 모델 이름, 질문 세트 버전, 입력 토큰 수도 같이 저장함. 나중에 비용과 정확도를 비교할 때 씀
- 1단계 항목 "Jev 호출 서비스"와 "질문 세트 정의"에 해당함. 재시도와 측정 로그는 다음 가이드에서 함

## 완료 기준
- `.\gradlew.bat test` 통과 (새 테스트 4개 포함, 전체 17개)
- 서버를 띄우고 `P1.mp4`를 올려 프레임을 추출한 뒤 판단 API를 부르면 `201 Created`와 판단 결과 16개가 옴

## 만들 API
| 요청 | 설명 | 성공 | 실패 |
|---|---|---|---|
| `POST /api/videos/{videoId}/judgments` | 아직 판단하지 않은 프레임을 Jev로 판단해 저장 | 201 + 전체 판단 목록 | 없는 영상 404, 프레임을 아직 안 뽑음 409 |
| `GET /api/videos/{videoId}/judgments` | 저장한 판단 목록 (시각 순) | 200 + 목록 | 없는 영상 404 |

응답 예시:
```json
[{"frameId": 64, "timeSec": 11.0, "questionSetVersion": "v2", "model": "jev-1.14.0", "inputTokens": 2803,
  "answers": {"paying": 0.04, "person": 0.99, "putting_in_bag": 0.06, "holding_product": 0.1}}]
```

---

## 1. 개념 먼저

### 1-1. 이번에 만드는 흐름
```
POST /judgments
  → 영상이 있나? (없으면 404)
  → 저장된 프레임 목록 조회 (없으면 409: 프레임을 먼저 뽑아야 함)
  → 이미 판단한 프레임 id 목록 조회
  → 아직 안 한 프레임마다: 이미지 파일 읽기 → Jev 호출 → 결과 저장
  → 전체 판단 목록 응답
```
- Jev 호출 코드(`JevClient`)는 0단계에서 이미 만들었음. 이번에는 그것을 **서비스에서 쓰고 결과를 저장하는 부분**을 만듦

### 1-2. 질문 세트를 기능 코드로 옮김 (`FrameQuestions`)
- 0단계에서는 질문 세트 v0~v3을 테스트 폴더에 두고 실험했음. 그중 v2를 고름 (결정 002)
- 기능 코드는 테스트 폴더의 클래스를 쓸 수 없어서, v2를 `main` 쪽 `FrameQuestions`로 옮김
- `VERSION = "v2"`를 같이 둠. 질문 문구를 바꾸면 이 값을 올림
  - 결과를 저장할 때 버전도 저장해서, 나중에 "v2로 물은 결과"와 "v3으로 물은 결과"를 구분해 비교할 수 있음
- `Collections.unmodifiableMap(...)`: 읽기 전용으로 감쌈. 다른 코드가 실수로 `QUESTIONS.put(...)`을 하면 바로 예외가 나서 질문이 몰래 바뀌는 일을 막음

### 1-3. 결과를 어떤 모양으로 저장하나 (결정 004)
질문 이름 → 확률(`{"person": 0.99, "holding_product": 0.1, ...}`)을 저장하는 방법은 세 가지가 있었음.

| 방법 | 장점 | 단점 |
|---|---|---|
| A. 질문마다 칸 (`person`, `holding_product` …) | 가장 단순함. SQL로 바로 비교 | 질문 세트를 바꿀 때마다 테이블을 고쳐야 함. v3은 질문이 7개 |
| B. 자식 테이블 (`judgment_id, question, probability` 한 줄씩) | 질문이 바뀌어도 테이블은 그대로 | 판단 하나에 4줄. 읽을 때마다 조인 필요 |
| **C. JSON 한 칸 (PostgreSQL `jsonb`)** | 질문이 바뀌어도 테이블 그대로. 한 줄로 읽음. SQL로도 검색 가능 | DB 종류에 묶임 (PostgreSQL 전용 기능) |

- **C를 고름.** 질문 세트는 앞으로도 바뀔 예정이고(v3 재시험), 읽을 때는 항상 판단 하나를 통째로 읽기 때문임
- PostgreSQL을 고른 이유 중 하나가 "JSON 저장·검색에 유리"였음 (handoff 2장)
- SQL에서 꺼내는 예: `select answers->>'person' from frame_judgments;`

### 1-4. JPA에서 JSON 칸 쓰기
```java
@JdbcTypeCode(SqlTypes.JSON)
@Column(nullable = false, columnDefinition = "jsonb")
private Map<String, Double> answers;
```
- `@JdbcTypeCode(SqlTypes.JSON)`: Hibernate가 저장할 때 `Map`을 JSON 글자로 바꾸고, 읽을 때 다시 `Map`으로 바꿔 줌
- `columnDefinition = "jsonb"`: 테이블을 만들 때 칸 종류를 `jsonb`로 지정함
  - `json`은 받은 글자를 그대로 저장하고, `jsonb`는 분석한 형태로 저장해서 검색이 빠름
- **`jsonb`는 키 순서를 지키지 않음.** 저장할 때 `person, holding_product, ...` 순서였어도 읽으면 `paying, person, ...`처럼 바뀔 수 있음. 이름으로 꺼내 쓰니 문제없음

### 1-5. 외부 API 호출은 트랜잭션 밖에서 함
- 가이드 02의 `FrameService.extract()`에는 `@Transactional`을 붙였음. 이번 `judge()`에는 **일부러 붙이지 않음**
- `@Transactional`이 붙은 메서드는 끝날 때까지 DB 연결 하나를 붙잡고 있음
  - Jev 호출은 프레임당 0.3~1초. 1시간 영상(3,600장)이면 몇십 분 동안 DB 연결을 붙잡음
  - DB 연결은 개수가 정해져 있어서(HikariCP 기본 10개), 이런 요청이 몇 개만 겹쳐도 다른 요청이 연결을 못 얻고 멈춤
- 그래서 프레임 하나를 판단할 때마다 `save()`로 바로 저장함. `save()`는 혼자서 짧은 트랜잭션을 열고 닫음
- 대신 중간에 실패하면 앞부분만 저장된 상태가 남음 → 다음 1-6으로 해결함

### 1-6. 다시 불러도 안전하게: 이미 한 프레임은 건너뜀
- `POST`를 다시 부르면 이미 판단한 프레임은 건너뛰고 남은 것만 Jev에 보냄
- 효과
  - 10장째에서 Jev가 실패해도, 다시 부르면 11장째부터 이어서 함 (앞의 10장 비용을 다시 내지 않음)
  - 같은 요청을 실수로 두 번 보내도 결과가 중복 저장되지 않음
- 이렇게 "여러 번 실행해도 결과가 한 번 실행한 것과 같은" 성질을 **멱등성**이라고 함. 다음 가이드의 재시도도 이 성질 위에서 안전하게 동작함
- 가이드 02의 프레임 추출은 두 번째 요청을 409로 막았음. 추출은 한 번에 끝나는 작업이고, 판단은 오래 걸리고 중간에 실패할 수 있어서 방식이 다름

### 1-7. fetch join과 N+1 문제
- 응답에 프레임 시각(`timeSec`)이 필요함. `FrameJudgment`의 `frame`은 `LAZY`라서 처음에는 비어 있음
- 그냥 `findAll`로 판단 16개를 읽고 하나씩 `getFrame().getTimeSec()`를 부르면:
  - 판단 목록 조회 1번 + 프레임 조회 16번 = **17번 쿼리** → 이것이 **N+1 문제**
- `join fetch`를 쓰면 판단과 프레임을 **한 번의 쿼리**로 같이 읽음
```sql
-- join fetch가 만드는 SQL (간단히)
select j.*, f.* from frame_judgments j
join video_frames f on f.id = j.frame_id
where f.video_id = ? and j.question_set_version = ?
order by f.time_sec
```
- 가이드 02 질문 3번("LAZY를 쓰지 않으면?")의 짝임. LAZY로 기본은 덜 읽고, **같이 필요할 때만 fetch join으로 한 번에** 읽음
- `@Query` 안의 글은 SQL이 아니라 JPQL임. 테이블 이름 대신 엔티티 이름(`FrameJudgment`)과 필드 이름(`j.frame`, `f.timeSec`)을 씀
- `"""..."""`: 여러 줄 문자열 (Java 15부터). 긴 쿼리를 줄바꿈해서 읽기 좋게 씀

### 1-8. 가짜 객체(mock)로 테스트하기
- 테스트에서 진짜 Jev를 부르면 돈이 들고, 인터넷이 필요하고, 답이 매번 조금씩 다름 (0단계에서 같은 입력으로 307·308·309개 일치)
- `@MockitoBean JevClient jevClient;`: 스프링이 진짜 `JevClient` 대신 **가짜 객체**를 넣어 줌
  - `when(jevClient.askYesNo(...)).thenReturn(fakeResponse)`: 이렇게 부르면 이 답을 돌려줘라
  - `verify(jevClient, times(5)).askYesNo(...)`: 정말 5번 불렸는지 확인
- 그래서 이 테스트가 확인하는 것은 "Jev가 맞게 판단하는가"가 아니라 **"우리 코드가 프레임을 보내고 답을 맞게 저장하는가"**임. Jev 판단의 정확도는 0단계처럼 `jevTest`(실제 호출)로 따로 잼

---

## 2. 미리 바꿔둔 설정
이번 가이드에서는 설정 파일을 바꾸지 않음.
- Jev 설정(`jev.base-url`, `jev.model`, `jev.api-key`)은 0단계에 이미 있음
- `frame_judgments` 테이블은 `ddl-auto=update`가 서버를 띄울 때 자동으로 만듦

---

## 3. 코드 작성
- 작업 브랜치: `feature/jev-analysis`
- 폴더와 빈 파일(테스트 포함)은 Claude Code가 미리 만들어 둠. 빈 파일에는 `package` 줄과 비어 있는 선언만 있음. 선언 줄까지 아래 코드로 통째로 바꾸면 됨
- `FrameService.java`는 기존 파일에 메서드 하나만 추가함

```
domain/analysis/
├── jev/FrameQuestions.java                   (새로 만듦)
├── entity/FrameJudgment.java                 (새로 만듦)
├── repository/FrameJudgmentRepository.java   (새로 만듦)
├── dto/FrameJudgmentResponse.java            (새로 만듦)
├── service/JudgmentService.java              (새로 만듦)
└── controller/JudgmentController.java        (새로 만듦)
domain/video/
└── service/FrameService.java                 (메서드 하나 추가)
test/.../domain/analysis/
└── controller/JudgmentControllerTest.java    (새로 만듦)
```

### 3-1. `analysis/jev/FrameQuestions.java`
```java
package com.mujinjigi.domain.analysis.jev;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 1단계 · 03 (Jev 프레임 판단)
 * 프레임 하나에 Jev로 보내는 질문 세트. 0단계 실험에서 고른 v2를 그대로 씀 (결정 002)
 * 질문 문구를 바꾸면 VERSION도 올려서, 어떤 질문으로 나온 결과인지 DB에서 구분되게 함
 */
public final class FrameQuestions {

    public static final String VERSION = "v2";

    public static final Map<String, String> QUESTIONS;

    static {
        Map<String, String> questions = new LinkedHashMap<>();
        questions.put("person", "Is there a person or part of a person (such as a hand) in the image `photo`?");
        questions.put("holding_product", "Is a person holding a store product (not a shopping bag, phone or wallet) in their hand in the image `photo`?");
        questions.put("putting_in_bag", "Is a person putting an item into a bag or pocket in the image `photo`? Putting a phone, wallet or other personal belonging into a bag, or taking one out, does not count.");
        questions.put("paying", "Is a person paying (by card, phone or cash) at a checkout or payment terminal in the image `photo`?");
        // 밖에서 질문을 바꾸지 못하게 읽기 전용으로 감쌈
        QUESTIONS = Collections.unmodifiableMap(questions);
    }

    private FrameQuestions() {
    }
}
```
- 질문 문장은 테스트 폴더의 `QuestionSetV2`와 **한 글자도 다르지 않아야 함**. 문구가 조금만 달라도 Jev 답이 달라져서 0단계 실험 결과를 그대로 믿을 수 없게 됨. 직접 치지 말고 `QuestionSetV2.java`와 `QuestionSetV0.java`에서 복사하는 것을 권장함
- 문장 안의 `` `photo` ``는 `JevClient.askYesNo(image, ...)`가 이미지에 붙이는 이름임. 질문이 어느 이미지를 보는지 가리킴
- `static { ... }`: 클래스가 처음 쓰일 때 한 번 실행되는 블록. `LinkedHashMap`으로 넣은 순서를 지킴
- `private FrameQuestions() {}`: 상수만 모아 둔 클래스라 `new`로 만들 일이 없어서 막아 둠

### 3-2. `analysis/entity/FrameJudgment.java`
```java
package com.mujinjigi.domain.analysis.entity;

import java.util.Map;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.mujinjigi.domain.video.entity.VideoFrame;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * 1단계 · 03 (Jev 프레임 판단)
 * 프레임 한 장에 대한 Jev 판단 결과. 질문마다 "예"일 확률을 저장함
 */
@Entity
@Table(name = "frame_judgments")
public class FrameJudgment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "frame_id")
    private VideoFrame frame;

    // 어떤 질문 세트로 물었는지 (v2). 질문을 바꾼 뒤 결과를 비교할 때 씀
    @Column(nullable = false)
    private String questionSetVersion;

    // Jev가 실제로 답한 모델 이름 (응답의 model 값)
    @Column(nullable = false)
    private String model;

    // 이 요청의 입력 토큰 수 (과금 대상). 비용 계산에 씀
    @Column(nullable = false)
    private int inputTokens;

    // 질문 이름 → "예"일 확률. 질문 세트가 바뀌어도 테이블을 고치지 않도록 JSON(jsonb) 한 칸에 담음
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Double> answers;

    protected FrameJudgment() {
    }

    public FrameJudgment(VideoFrame frame, String questionSetVersion, String model, int inputTokens,
                         Map<String, Double> answers) {
        this.frame = frame;
        this.questionSetVersion = questionSetVersion;
        this.model = model;
        this.inputTokens = inputTokens;
        this.answers = answers;
    }

    public Long getId() {
        return id;
    }

    public VideoFrame getFrame() {
        return frame;
    }

    public String getQuestionSetVersion() {
        return questionSetVersion;
    }

    public String getModel() {
        return model;
    }

    public int getInputTokens() {
        return inputTokens;
    }

    public Map<String, Double> getAnswers() {
        return answers;
    }
}
```
- 다른 domain(`video`)의 엔티티 `VideoFrame`을 `@ManyToOne`으로 가리킴. 프레임 하나에 질문 세트 버전마다 판단이 하나씩 생길 수 있어서 "여러 판단 → 프레임 하나"임
- `model`은 설정 값(`jev.model`)이 아니라 **응답의 `model` 값**을 저장함. 설정과 실제로 답한 모델이 다를 수 있어서, 실제 값을 남겨야 나중에 결과 차이를 추적할 수 있음 (handoff 6장)
- `jakarta.persistence`가 아닌 `org.hibernate`의 어노테이션(`@JdbcTypeCode`)이 처음 나옴. JPA 표준에 JSON 칸이 없어서 Hibernate 기능을 씀

### 3-3. `analysis/repository/FrameJudgmentRepository.java`
```java
package com.mujinjigi.domain.analysis.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.mujinjigi.domain.analysis.entity.FrameJudgment;

/**
 * 1단계 · 03 (Jev 프레임 판단)
 * FrameJudgment 저장·조회
 */
public interface FrameJudgmentRepository extends JpaRepository<FrameJudgment, Long> {

    // 판단 결과와 프레임을 한 번의 쿼리로 같이 읽음 (fetch join). 응답에 프레임 시각이 필요해서
    // 이렇게 안 하면 결과 N개를 읽은 뒤 프레임을 N번 더 조회함 (N+1 문제)
    @Query("""
            select j from FrameJudgment j
            join fetch j.frame f
            where f.video.id = :videoId and j.questionSetVersion = :version
            order by f.timeSec
            """)
    List<FrameJudgment> findAllWithFrame(Long videoId, String version);
}
```
- `:videoId`, `:version`은 메서드 파라미터 이름과 같아야 함. 이름으로 값을 찾아 넣음
- 가이드 02의 `findByVideoIdOrderByTimeSecAsc`처럼 메서드 이름으로 만들 수도 있지만, 이름 규칙으로는 fetch join을 지정할 수 없어서 `@Query`로 직접 씀

### 3-4. `analysis/dto/FrameJudgmentResponse.java`
```java
package com.mujinjigi.domain.analysis.dto;

import java.util.Map;

import com.mujinjigi.domain.analysis.entity.FrameJudgment;

/**
 * 1단계 · 03 (Jev 프레임 판단)
 * 프레임 판단 결과 응답
 */
public record FrameJudgmentResponse(Long frameId, double timeSec, String questionSetVersion, String model,
                                    int inputTokens, Map<String, Double> answers) {

    public static FrameJudgmentResponse from(FrameJudgment judgment) {
        return new FrameJudgmentResponse(
                judgment.getFrame().getId(),
                judgment.getFrame().getTimeSec(),
                judgment.getQuestionSetVersion(),
                judgment.getModel(),
                judgment.getInputTokens(),
                judgment.getAnswers());
    }
}
```
- `judgment.getFrame().getTimeSec()`: 여기서 프레임을 읽음. fetch join으로 이미 읽어 두었기 때문에 추가 쿼리가 나가지 않음

### 3-5. `video/service/FrameService.java` (메서드 추가)
기존 `findAll()` 메서드 아래, `private Video findVideo(...)` 위에 추가함. 클래스 맨 위 주석의 `1단계 · 02 (프레임 추출과 고르기)` 줄 아래에 ` * 1단계 · 03 (imagePath 추가)`도 넣음.
```java
    // 프레임 이미지 파일의 실제 경로 (Jev에 보낼 때 씀). 프레임 폴더 위치는 이 클래스만 앎
    public Path imagePath(Long videoId, String fileName) {
        return frameDir.resolve(String.valueOf(videoId)).resolve(fileName);
    }
```
- 가이드 02의 `VideoFileStorage.pathOf()`와 같은 이유임. 프레임 폴더 규칙(`frames/{영상id}/파일이름`)을 `FrameService` 한 곳에만 둠. `JudgmentService`가 경로를 직접 조합하면 규칙이 두 군데에 생김

### 3-6. `analysis/service/JudgmentService.java`
```java
package com.mujinjigi.domain.analysis.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.mujinjigi.domain.analysis.dto.FrameJudgmentResponse;
import com.mujinjigi.domain.analysis.entity.FrameJudgment;
import com.mujinjigi.domain.analysis.jev.FrameQuestions;
import com.mujinjigi.domain.analysis.jev.JevClient;
import com.mujinjigi.domain.analysis.jev.JevResponse;
import com.mujinjigi.domain.analysis.repository.FrameJudgmentRepository;
import com.mujinjigi.domain.video.entity.VideoFrame;
import com.mujinjigi.domain.video.repository.VideoFrameRepository;
import com.mujinjigi.domain.video.repository.VideoRepository;
import com.mujinjigi.domain.video.service.FrameService;

/**
 * 1단계 · 03 (Jev 프레임 판단)
 * 저장된 프레임을 Jev에 보내 판단 결과를 저장함
 */
@Service
public class JudgmentService {

    private final VideoRepository videoRepository;
    private final VideoFrameRepository videoFrameRepository;
    private final FrameJudgmentRepository frameJudgmentRepository;
    private final FrameService frameService;
    private final JevClient jevClient;

    public JudgmentService(VideoRepository videoRepository,
                           VideoFrameRepository videoFrameRepository,
                           FrameJudgmentRepository frameJudgmentRepository,
                           FrameService frameService,
                           JevClient jevClient) {
        this.videoRepository = videoRepository;
        this.videoFrameRepository = videoFrameRepository;
        this.frameJudgmentRepository = frameJudgmentRepository;
        this.frameService = frameService;
        this.jevClient = jevClient;
    }

    // @Transactional을 붙이지 않음. Jev 호출(프레임당 약 1초)을 하는 동안 DB 연결을 붙잡지 않으려고,
    // 프레임 하나를 판단할 때마다 save()로 바로 저장함 (save()는 그 자체로 짧은 트랜잭션)
    public List<FrameJudgmentResponse> judge(Long videoId) {
        checkVideoExists(videoId);
        List<VideoFrame> frames = videoFrameRepository.findByVideoIdOrderByTimeSecAsc(videoId);
        if (frames.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "프레임을 먼저 추출해야 함: " + videoId);
        }

        // 이미 판단한 프레임은 건너뜀. 중간에 실패해도 다시 부르면 남은 프레임부터 이어서 함
        Set<Long> judgedFrameIds = frameJudgmentRepository.findAllWithFrame(videoId, FrameQuestions.VERSION).stream()
                .map(judgment -> judgment.getFrame().getId())
                .collect(Collectors.toSet());

        for (VideoFrame frame : frames) {
            if (!judgedFrameIds.contains(frame.getId())) {
                frameJudgmentRepository.save(judgeOne(videoId, frame));
            }
        }
        return findAll(videoId);
    }

    public List<FrameJudgmentResponse> findAll(Long videoId) {
        checkVideoExists(videoId);
        return frameJudgmentRepository.findAllWithFrame(videoId, FrameQuestions.VERSION).stream()
                .map(FrameJudgmentResponse::from)
                .toList();
    }

    private FrameJudgment judgeOne(Long videoId, VideoFrame frame) {
        byte[] image = readImage(videoId, frame);
        JevResponse response = jevClient.askYesNo(image, "jpg", FrameQuestions.QUESTIONS);

        // 응답 형식(Answer 객체)을 그대로 저장하지 않고, 질문 이름 → 확률만 꺼내서 저장함
        Map<String, Double> answers = new LinkedHashMap<>();
        response.answers().forEach((name, answer) -> answers.put(name, answer.noul()));

        return new FrameJudgment(frame, FrameQuestions.VERSION, response.model(),
                response.usage().inputTokens(), answers);
    }

    private byte[] readImage(Long videoId, VideoFrame frame) {
        try {
            return Files.readAllBytes(frameService.imagePath(videoId, frame.getFileName()));
        } catch (IOException e) {
            throw new UncheckedIOException("프레임 이미지를 읽지 못함: " + frame.getFileName(), e);
        }
    }

    private void checkVideoExists(Long videoId) {
        if (!videoRepository.existsById(videoId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "영상이 없음: " + videoId);
        }
    }
}
```
- `analysis`가 `video`의 Repository와 `FrameService`를 씀. 반대(`video`가 `analysis`를 씀)는 없음. 의존 방향을 한쪽으로만 두면 나중에 모듈을 나눌 때 끊을 곳이 분명함
- `Set<Long> judgedFrameIds`: 프레임마다 "이미 판단했나?"를 DB에 묻지 않고(프레임 수만큼 쿼리), 처음에 한 번 읽어 `Set`에 담아 확인함
- `checkVideoExists()`는 `existsById()`를 씀. 영상 객체가 필요 없고 "있나?"만 알면 되어서, 가이드 02의 `findVideo()`보다 가벼움
- `"jpg"`: 가이드 02에서 프레임을 jpg로 저장했음 (`frame_0001.jpg`)
- `IOException`을 `UncheckedIOException`으로 감싸는 이유는 가이드 02와 같음. 메서드마다 `throws IOException`을 붙이지 않아도 되고, 처리되지 않으면 500으로 응답함

### 3-7. `analysis/controller/JudgmentController.java`
```java
package com.mujinjigi.domain.analysis.controller;

import java.net.URI;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mujinjigi.domain.analysis.dto.FrameJudgmentResponse;
import com.mujinjigi.domain.analysis.service.JudgmentService;

/**
 * 1단계 · 03 (Jev 프레임 판단)
 * 프레임 Jev 판단 API
 */
@RestController
@RequestMapping("/api/videos/{videoId}/judgments")
public class JudgmentController {

    private final JudgmentService judgmentService;

    public JudgmentController(JudgmentService judgmentService) {
        this.judgmentService = judgmentService;
    }

    // POST → 201 + 판단 목록. 이미 판단한 프레임은 건너뜀. 없는 영상 404, 프레임을 아직 안 뽑았으면 409
    @PostMapping
    public ResponseEntity<List<FrameJudgmentResponse>> judge(@PathVariable Long videoId) {
        List<FrameJudgmentResponse> judgments = judgmentService.judge(videoId);
        return ResponseEntity.created(URI.create("/api/videos/" + videoId + "/judgments")).body(judgments);
    }

    // GET → 200 + 시각 순 판단 목록
    @GetMapping
    public List<FrameJudgmentResponse> findAll(@PathVariable Long videoId) {
        return judgmentService.findAll(videoId);
    }
}
```
- 가이드 02의 `VideoFrameController`와 같은 모양임. 주소가 `/api/videos/{videoId}/...`라 영상 아래에 있지만, 코드는 `analysis` domain에 둠. 주소는 "무엇에 대한 자원인가", 패키지는 "어떤 기능인가"로 나눔

### 3-8. 테스트: `analysis/controller/JudgmentControllerTest.java`
`src/test/java/com/mujinjigi/domain/analysis/controller/` 아래에 있음.
```java
package com.mujinjigi.domain.analysis.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.JsonPath;
import com.mujinjigi.domain.analysis.jev.FrameQuestions;
import com.mujinjigi.domain.analysis.jev.JevClient;
import com.mujinjigi.domain.analysis.jev.JevResponse;
import com.mujinjigi.domain.video.TestVideos;

/**
 * 1단계 · 03 (Jev 프레임 판단)
 * 프레임 판단 API 테스트. 진짜 Jev 대신 가짜(JevClient mock)가 정해 둔 답을 돌려줌 (비용·네트워크 없이 실행)
 */
@SpringBootTest(properties = {
        "app.storage.video-dir=build/test-uploads",
        "app.storage.frame-dir=build/test-frames"})
@AutoConfigureMockMvc
@Transactional
class JudgmentControllerTest {

    @Autowired
    MockMvc mockMvc;

    // 스프링이 진짜 JevClient 대신 이 가짜를 주입함
    @MockitoBean
    JevClient jevClient;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        // 어떤 이미지를 보내도 같은 답을 돌려주게 정해 둠
        JevResponse fakeResponse = new JevResponse(
                "jev-1.14.0",
                Map.of("person", new JevResponse.Answer("noul", 0.9),
                        "putting_in_bag", new JevResponse.Answer("noul", 0.1)),
                new JevResponse.Usage(2803, 0));
        when(jevClient.askYesNo(any(byte[].class), eq("jpg"), eq(FrameQuestions.QUESTIONS)))
                .thenReturn(fakeResponse);
    }

    @Test
    void 프레임을_판단하면_결과를_저장하고_돌려준다() throws Exception {
        long videoId = uploadAndExtract();

        // 테스트 영상은 프레임 5장(1~5초) → Jev 5번 호출
        mockMvc.perform(post("/api/videos/{id}/judgments", videoId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.length()").value(5))
                .andExpect(jsonPath("$[0].timeSec").value(1.0))
                .andExpect(jsonPath("$[0].questionSetVersion").value("v2"))
                .andExpect(jsonPath("$[0].model").value("jev-1.14.0"))
                .andExpect(jsonPath("$[0].inputTokens").value(2803))
                .andExpect(jsonPath("$[0].answers.person").value(0.9));
        verify(jevClient, times(5)).askYesNo(any(byte[].class), eq("jpg"), eq(FrameQuestions.QUESTIONS));

        mockMvc.perform(get("/api/videos/{id}/judgments", videoId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5));
    }

    @Test
    void 다시_판단하면_이미_판단한_프레임은_건너뛴다() throws Exception {
        long videoId = uploadAndExtract();
        mockMvc.perform(post("/api/videos/{id}/judgments", videoId)).andExpect(status().isCreated());

        mockMvc.perform(post("/api/videos/{id}/judgments", videoId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.length()").value(5));
        // 두 번 불렀지만 Jev 호출은 처음의 5번뿐임
        verify(jevClient, times(5)).askYesNo(any(byte[].class), eq("jpg"), eq(FrameQuestions.QUESTIONS));
    }

    @Test
    void 프레임을_추출하지_않은_영상은_409를_돌려준다() throws Exception {
        long videoId = upload();

        mockMvc.perform(post("/api/videos/{id}/judgments", videoId))
                .andExpect(status().isConflict());
    }

    @Test
    void 없는_영상은_404를_돌려준다() throws Exception {
        mockMvc.perform(post("/api/videos/{id}/judgments", 999_999))
                .andExpect(status().isNotFound());
    }

    // 테스트 영상을 올리고 프레임까지 추출한 뒤 영상 id를 돌려줌
    private long uploadAndExtract() throws Exception {
        long videoId = upload();
        mockMvc.perform(post("/api/videos/{id}/frames", videoId)).andExpect(status().isCreated());
        return videoId;
    }

    private long upload() throws Exception {
        Path video = TestVideos.blackThenWhite(tempDir);
        MockMultipartFile file = new MockMultipartFile("file", "test.mp4", "video/mp4", Files.readAllBytes(video));

        String body = mockMvc.perform(multipart("/api/videos").file(file))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Number id = JsonPath.read(body, "$.id");
        return id.longValue();
    }
}
```
- `any(byte[].class)`: 어떤 바이트 배열이든 상관없음. `JevClient`에는 `askYesNo`가 두 개(이미지 한 장 / 여러 장) 있어서, `byte[]`라고 적어야 한 장짜리를 가리킴
- `eq(FrameQuestions.QUESTIONS)`: 질문 세트는 정확히 v2여야 함. 다른 질문을 보내면 가짜가 답을 안 줘서(null) 테스트가 실패함
- `JsonPath.read(...)`: 가이드 02 `VideoFrameControllerTest`에서 쓴 방식과 같음
- 테스트 영상(검은 화면 3초 → 흰 화면 3초)에서는 1~5초 프레임 5장이 남음 (가이드 02와 같음)
- 테스트가 트랜잭션 안에서 돌아서(`@Transactional`) 서비스의 `save()`도 같은 트랜잭션에 들어가고, 테스트가 끝나면 모두 되돌림

---

## 4. 확인

### 4-1. 테스트
Docker PostgreSQL이 켜져 있어야 함 (`docs/guide/README.md` 참고).
```powershell
cd backend
$env:JAVA_HOME = "$env:USERPROFILE\.jdks\ms-21.0.12"
.\gradlew.bat test
```
- `BUILD SUCCESSFUL`이 나오면 통과임 (전체 17개)
- 이 테스트는 진짜 Jev를 부르지 않아서 `.env`에 키가 없어도 통과함

### 4-2. 실제 서버로 확인 (진짜 Jev 호출, 약 0.002달러)
서버 실행 (backend 폴더에서):
```powershell
.\gradlew.bat bootRun
```
다른 터미널에서 (저장소 루트에서). 업로드 응답의 `id`로 바꿔서 차례로 부름:
```powershell
curl.exe -i -F "file=@docs/test-data/videos/P1.mp4" http://localhost:8080/api/videos
curl.exe -i -X POST http://localhost:8080/api/videos/3/frames
curl.exe -i -X POST http://localhost:8080/api/videos/3/judgments
```
- 판단 16개가 오고, 모두 `"model":"jev-1.14.0"`, `"inputTokens":2803`이면 됨. 5초 안팎 걸림
- Claude Code가 확인한 값 (2026-10-30, 실행마다 소수 둘째 자리는 조금 다를 수 있음)

| 시각 | person | holding_product | putting_in_bag | 해석 |
|---|---|---|---|---|
| 0~9초 | 0.06~0.08 | 0.02~0.03 | 0.02 | 빈 매장 |
| 11초 | 0.99 | 0.10 | 0.06 | 손님 들어옴 |
| 14~15초 | 0.98~0.99 | **0.97~0.98** | 0.14~0.16 | 상품을 듦 |
| 16초 | 0.99 | 0.56 | **0.28** | 가방에 넣는 중 (뒤에서 찍혀 손이 가려짐) |

- P1은 손님이 상품을 가방에 넣는 영상인데 `putting_in_bag`이 0.28에 그침. 0단계에서 본 그대로, 카메라가 뒤에서 찍어 넣는 동작이 안 보이기 때문임. **행동 판단만으로는 놓친다**는 결정 001의 근거가 실제 서버에서도 그대로 나옴
- 같은 POST를 한 번 더 부르면 처음보다 훨씬 빨리 같은 16개가 옴. Jev를 다시 부르지 않았다는 뜻임
- DB에서 직접 보기 (선택):
```powershell
docker exec -it mujinjigi-postgres psql -U <DB_USERNAME> -d <DB_NAME> -c "select frame_id, answers->>'person' as person from frame_judgments order by frame_id;"
```
  - `<DB_USERNAME>`, `<DB_NAME>`은 `.env`의 값으로 바꿈

---

## 5. 알려진 한계 (면접에서 물어볼 만한 부분)
1. **한 장씩 차례로 보냄 (순차 호출)**
   - P1 16장에 약 4.6초(장당 0.3초). 하루 활동 4시간(14,400장)이면 약 70분이 걸림
   - 여러 장을 동시에 보내면(병렬) 빨라지지만, Jev 요청 한도가 공개되지 않아 429(한도 초과)를 맞을 수 있음 → 재시도를 먼저 만들고, 동시 개수를 늘려 가며 측정함 (성능 기록 후보)
2. **실패하면 그 자리에서 멈추고 500이 남**: 재시도가 없음. 다시 부르면 이어서 하긴 하지만, 사람이 다시 불러야 함 → 다음 가이드에서 429·529·네트워크 오류를 자동 재시도함
3. **요청이 끝날 때까지 기다림 (동기)**: 가이드 02와 같은 문제. 2단계에서 작업(Job)으로 바꿈
4. **같은 영상에 POST가 동시에 두 번 오면 중복 저장될 수 있음**: 둘 다 "아직 안 한 프레임"을 같은 시점에 읽고 둘 다 저장함. `(frame_id, question_set_version)`에 유니크 제약을 걸면 DB가 막아 줌 → 작업(Job)으로 바꿀 때 같이 처리함
5. **요청 원문(보낸 질문, 받은 응답 전체)은 저장하지 않음**: 확률·모델·토큰만 남김. 문제가 생겼을 때 정확한 요청을 다시 보기 어려움 → 다음 가이드의 "요청·응답 로그"에서 다룸
6. **질문 세트 버전을 바꾸면 처음부터 다시 판단함**: 버전별로 따로 저장하는 구조라 의도한 동작임. 비교 실험에는 좋지만 비용은 두 배가 됨

---

## 6. 다 했으면
"가이드 03 다 했어"라고 알려 주세요. 이렇게 진행합니다.
1. 작성한 코드 리뷰
2. 테스트 실행
3. 통과하면 학습 노트 작성과 기능 단위 커밋

## 스스로 답해볼 질문
1. Jev 호출을 `@Transactional` 메서드 안에서 하면 어떤 문제가 생기는가. 대신 무엇을 포기했고 어떻게 보완했는가 (면접 질문)
2. 판단 결과를 질문별 칸이 아니라 `jsonb` 한 칸에 저장한 이유는 무엇인가. 단점은 무엇인가
3. fetch join이 없으면 판단 목록 조회에서 쿼리가 몇 번 나가는가. 왜 그런가 (N+1, 면접 질문)
4. 같은 판단 요청을 두 번 보내도 결과가 중복되지 않게 한 방법은 무엇인가. 이 성질이 재시도에 왜 중요한가
5. 테스트에서 `JevClient`를 가짜로 바꾸면 무엇을 확인할 수 있고, 무엇은 확인할 수 없는가
