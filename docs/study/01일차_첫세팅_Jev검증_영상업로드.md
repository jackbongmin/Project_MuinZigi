# 01일차 · 첫 세팅, Jev 검증, 영상 업로드 API

- 날짜: 2026-10-28
- 단계: 0단계(첫 세팅과 검증) → 1단계 시작

## 오늘 한 것
- 저장소 첫 세팅 (handoff.md 12장)
- Claude 기여자 문구를 두 겹으로 차단: Claude Code 설정(attribution) + git commit-msg 훅
- Claude Code가 `.env`를 읽지 못하도록 권한 거부 규칙 추가 (`.env.example`은 허용)
- `.gitignore` 정리, `.env.example` 추가, 훅 줄바꿈 보호용 `.gitattributes` 추가

## 바뀐 파일
- `.claude/settings.json`: Claude Code 공유 설정. attribution 끄기, `.env` 읽기 거부
- `scripts/git-hooks/commit-msg`: 커밋 메시지에서 Claude 관련 줄을 지우는 훅
- `.gitattributes`: 훅 파일을 항상 LF 줄바꿈으로 유지
- `.gitignore`: GitHub 기본 Java 템플릿에 `.env`, 영상·프레임, Gradle·Node 빌드 결과물, IDE 설정을 추가
- `.env.example`: 필요한 환경변수 키 이름만 적은 견본

## 핵심 개념

### 1. Git 훅 (hook)
- git이 특정 시점에 자동으로 실행하는 스크립트임
- `commit-msg` 훅은 커밋 메시지를 다 쓴 직후, 커밋이 확정되기 직전에 실행됨. 메시지 파일 경로가 첫 번째 인자(`$1`)로 넘어옴
- 이 파일을 고치면 최종 커밋 메시지가 바뀌고, 0이 아닌 값으로 끝나면 커밋 자체가 취소됨
- 훅은 원래 `.git/hooks/`에 있어서 저장소에 커밋되지 않음. `git config core.hooksPath scripts/git-hooks`로 위치를 바꾸면 훅을 일반 파일처럼 커밋해서 공유할 수 있음
- 단, `core.hooksPath`는 PC마다 따로 설정해야 함 (git 설정은 저장소에 올라가지 않음)

### 2. sed로 줄 지우기
- `sed -e '/패턴/d' 파일`: 패턴과 맞는 줄을 지움 (`d` = delete)
- `-i.bak`: 원본 파일을 직접 고치고 백업을 `.bak`으로 남김. 백업은 바로 `rm`으로 지움
- `[Cc]`처럼 대괄호는 "둘 중 아무 글자"라는 뜻이라 대소문자를 모두 잡음

### 3. 줄바꿈 문자 (LF vs CRLF)
- Windows는 줄 끝에 `\r\n`(CRLF), Linux·macOS는 `\n`(LF)을 씀
- 이 PC는 `core.autocrlf=true`라서 git이 파일을 꺼낼 때 CRLF로 바꿀 수 있음
- 셸 스크립트 첫 줄이 `#!/bin/sh\r`이 되면 실행기를 못 찾아서 훅이 동작하지 않음
- `.gitattributes`에 `eol=lf`를 지정해 이 파일들은 항상 LF로 유지함

### 4. Claude Code 설정 파일과 우선순위
- `.claude/settings.json`: 저장소에 커밋되는 공유 설정 (오늘 사용)
- `.claude/settings.local.json`: 이 PC에서만 쓰는 개인 설정, 커밋하지 않음
- 우선순위: 관리자 설정 > 명령줄 > local > 공유 프로젝트 > 사용자(`~/.claude`)

### 5. attribution 설정
- Claude Code가 커밋에 붙이는 `Co-Authored-By` 줄과 PR 설명의 문구를 정하는 설정
- 옛 설정 `includeCoAuthoredBy`는 지원 중단(deprecated)되었고 `attribution`으로 바뀜
- 끄는 방법은 두 가지:
  - `"attribution": false` (v2.1.281 이상만 지원)
  - `commit`, `pr`을 빈 문자열, `sessionUrl`을 `false`로 설정 (예전 버전도 지원)
- 두 번째 방법을 고름. 첫 번째 방법은 예전 버전이 값을 거부하고 **설정 파일 전체를 건너뜀**. 그러면 같은 파일의 `.env` 차단 규칙까지 함께 꺼짐

### 6. 권한 거부 규칙 (permissions.deny)
- 규칙은 deny → ask → allow 순서로 검사함. allow로는 deny에 예외를 만들 수 없음
- 그래서 `.env.example` 예외는 deny 목록 안에서 `!`(gitignore의 부정 패턴)로 만듦
  - `Read(.env)`: 모든 폴더의 `.env`
  - `Read(.env.*)`: `.env.local`, `.env.prod` 등
  - `Read(!.env.example)`: 앞의 규칙에서 `.env.example`만 빼줌
- Read 거부는 Edit·Write 도구와 `cat`, `head` 같은 인식 가능한 셸 명령도 막음
- **한계**: 파일 이름을 직접 쓰지 않는 명령(`grep -r`)이나 스크립트가 파일을 여는 경우는 못 막음. 운영체제 수준 차단(sandbox)은 macOS·Linux·WSL2만 지원하고 Windows 기본 환경에서는 안 됨. 그래서 마지막 방어선은 "비밀값은 `.env`에만 둔다"는 규칙 자체임

## 코드 흐름
- `git commit` 실행 → git이 메시지를 `.git/COMMIT_EDITMSG`에 씀 → `core.hooksPath`의 `commit-msg` 실행 → sed가 Claude 관련 줄 삭제 → 고쳐진 메시지로 커밋 확정
- Claude Code가 파일을 읽으려 함 → `.claude/settings.json`의 deny 규칙 검사 → `.env` 계열이면 거부, `.env.example`이면 통과

## 확인한 것
- 임시 저장소에서 Claude 문구 3줄을 넣고 커밋함. 3줄 모두 지워지고 `.bak` 파일도 남지 않음
- `git check-ignore`: `.env`, `.env.local`, 영상, 빌드 결과물은 제외되고 `.env.example`은 추적됨
- Claude Code에서 `.env.example` 읽기는 성공함. `.env.*` 경로는 쓰기까지 거부됨

---

# 2부 · 0단계: Spring Boot 프로젝트와 PostgreSQL

## 오늘 한 것
- Spring Initializr로 `backend/` 생성 (Spring Boot 4.1.2, Java 21, Gradle 9.8.2)
- handoff.md 4장 패키지 구조 생성 (`domain/*`, `global`)
- Docker로 PostgreSQL 18 실행, Spring에서 연결 확인
- Jev 공식 문서를 확인하고 handoff.md 6장을 바로잡음
- 포트 충돌 문제 해결 → `docs/portfolio/troubleshooting/2026-10-28_DB_포트_충돌.md`

## 바뀐 파일
- `backend/build.gradle`: 사용할 라이브러리 목록 (web, data-jpa, postgresql, validation)
- `backend/src/main/java/com/mujinjigi/MujinjigiApplication.java`: 서버 시작점
- `backend/src/main/java/com/mujinjigi/**/package-info.java`: 패키지별 용도 설명 (git은 빈 폴더를 저장하지 않아서 넣어둠)
- `backend/src/main/resources/application.properties`: `.env` 읽기, DB 접속 설정
- `infra/docker-compose.yml`: 개발용 PostgreSQL 컨테이너
- `.env.example`: 개발용 기본값과 사용법 추가

## 핵심 개념

### 1. Spring Boot
- Java로 웹 서버를 만드는 프레임워크임
- 웹 서버(Tomcat)를 내장하고 있어서 `main` 메서드 하나로 서버가 뜸
- **자동 구성(auto-configuration)**: 라이브러리를 추가하면 설정을 알아서 맞춰줌. 예를 들어 `postgresql` 드라이버와 `spring.datasource.url`이 있으면 DB 연결 풀(HikariCP)을 자동으로 만듦
- `@SpringBootApplication`이 붙은 클래스가 시작점이고, 같은 패키지와 그 하위 패키지를 훑어 부품(Bean)을 찾음. 그래서 모든 코드를 `com.mujinjigi` 아래에 둠

### 2. Gradle과 Gradle Wrapper
- **Gradle**: 빌드 도구임. 라이브러리 내려받기, 컴파일, 테스트, 실행을 담당함
- **`build.gradle`**: 무엇을 쓸지 적는 파일
  - `implementation`: 실행할 때 필요한 라이브러리
  - `runtimeOnly`: 실행할 때만 필요한 라이브러리 (DB 드라이버)
  - `testImplementation`: 테스트에만 필요한 라이브러리
- **`gradlew`**: 정해진 Gradle 버전을 자동으로 받아 쓰는 스크립트(Wrapper)임. PC에 Gradle을 설치하지 않아도 되고, 누가 빌드해도 같은 버전을 씀
- `toolchain`의 `JavaLanguageVersion.of(21)`: Java 21로 컴파일하라는 뜻

### 3. JPA, Hibernate, DataSource
- **JPA**: Java 객체(엔티티)를 DB 테이블에 대응시키는 표준임(ORM). SQL을 직접 쓰지 않고 객체로 저장·조회함
- **Hibernate**: JPA를 실제로 구현한 라이브러리임. Spring Data JPA가 내부에서 사용함
- **DataSource / HikariCP**: DB 연결을 미리 몇 개 만들어 두고 돌려 쓰는 연결 풀(pool)임. 요청마다 새로 연결하면 느리기 때문
- `ddl-auto=update`: 엔티티를 보고 테이블을 자동으로 만들거나 고침. 개발할 때만 편하게 쓰고, 배포 전에는 Flyway 같은 마이그레이션 도구로 바꿀 예정임
- `open-in-view=false`: 요청이 끝날 때까지 DB 연결을 붙잡아 두는 기본 동작을 끔. 서비스 계층 밖에서 DB를 몰래 조회하는 일을 막음

### 4. Docker와 docker compose
- **이미지**: 프로그램과 실행 환경을 묶은 설치본 (`postgres:18`)
- **컨테이너**: 이미지를 실행한 것. PC와 격리되어 돌아감
- **포트 연결** `"5433:5432"`: PC의 5433번으로 들어온 요청을 컨테이너 안의 5432번으로 넘김
- **볼륨**: 컨테이너 밖에 데이터를 저장하는 공간. 컨테이너를 지워도 데이터가 남음
- **compose**: 여러 컨테이너 설정을 `docker-compose.yml` 하나에 적고 `up` 한 번으로 띄움

### 5. 설정값을 밖에서 주입하기
- 비밀번호 같은 값을 코드에 적지 않고 `.env`에 둠. 코드는 `${DB_PASSWORD}`처럼 이름으로만 참조함
- `spring.config.import=optional:file:../.env[.properties]`: Spring이 `.env`를 설정 파일처럼 읽게 함. `optional:`은 파일이 없어도 오류를 내지 않는다는 뜻
- 기본값 문법 차이 (오늘 실제로 걸린 부분)
  - Spring `${DB_PORT:5433}`: 키가 **없을 때만** 기본값을 씀
  - compose `${DB_PORT:-5433}`: 키가 없거나 **비어 있어도** 기본값을 씀
  - compose `${DB_PASSWORD:?안내문}`: 값이 비어 있으면 안내문을 띄우고 실행을 멈춤

## 코드 흐름
- `./gradlew test` 실행 → `MujinjigiApplicationTests`의 `@SpringBootTest`가 앱 전체를 띄움 → `application.properties` 읽기 → `../.env` 읽기 → `${...}` 자리에 값 채우기 → HikariCP가 `localhost:5433`의 PostgreSQL에 연결 → Hibernate가 DB 버전을 확인함(18.6) → 앱 기동 성공 = 테스트 통과
- 즉, 지금의 기본 테스트는 "설정과 DB 연결이 맞는가"를 확인하는 역할을 함

---

# 3부 · 0단계: Jev 이미지 요청 보내기

## 오늘 한 것
- `JevClient`: 이미지 1장 + 예/아니오 질문들을 Jev에 보내고 확률을 받음
- `JevClientLiveTest`: 코드로 그린 이미지(흰 바탕에 빨간 사각형)로 실제 호출함
  - 결과: "빨간 사각형?" 0.980, "파란 원?" 0.010
  - 입력 758토큰, 881ms
- 실제 API를 부르는 테스트는 `./gradlew jevTest`로만 실행되게 분리함
- `JevPhotoLiveTest`: 무료 사진 8장에 질문 4개를 보냄
  - 30개 판단 중 29개가 정답과 일치함
  - 핵심인 "넣는 동작" 사진이 부족해 0단계는 아직 미완료
  - 결과 문서: `docs/test-data/2026-10-28_jev_사진_테스트.md`
- 실험 설계 원칙: 정답을 Jev 호출 **전에** 정해 둠. 결과를 보고 정답을 맞추면 측정이 의미 없어지기 때문

## 바뀐 파일
- `backend/src/main/java/com/mujinjigi/domain/analysis/jev/JevClient.java`: Jev HTTP 호출
- `backend/src/main/java/com/mujinjigi/domain/analysis/jev/JevResponse.java`: 응답 JSON을 담는 record
- `backend/src/test/java/com/mujinjigi/domain/analysis/jev/JevClientLiveTest.java`: 실제 호출 테스트
- `backend/build.gradle`: `jev` 태그 테스트 분리, `jevTest` 작업 추가
- `backend/src/main/resources/application.properties`: `jev.*` 설정 (키는 `.env`에서)

## 핵심 개념

### 1. RestClient
- 우리 서버가 **요청을 보내는 쪽**이 될 때 쓰는 Spring의 HTTP 클라이언트임
- `RestClient.builder()`: 기본 주소(baseUrl)와 모든 요청에 붙일 헤더(Authorization)를 한 번 정해 둠
- `post().uri().contentType().header().body().retrieve().body(타입)`: 요청을 만들고 보낸 뒤, 응답 JSON을 지정한 Java 타입으로 바꿈
- 응답이 4xx이면 `HttpClientErrorException`, 5xx이면 `HttpServerErrorException`이 발생함

### 2. 생성자 주입과 @Value
- `@Component`: Spring이 이 클래스의 객체를 하나 만들어 관리하게 함(Bean)
- `@Value("${jev.api-key}")`: 설정값을 생성자 인자로 받음. 코드에는 키가 없고 실행할 때 `.env`에서 들어옴
- 필드를 `final`로 두면 객체가 만들어진 뒤 값이 바뀌지 않음

### 3. record와 JSON 변환
- `record JevResponse(String model, ...)`: 생성자, getter, equals를 자동으로 만들어주는 값 전용 클래스임
- Jackson이 JSON 키 이름과 record 필드 이름을 맞춰서 값을 채움
- 이름이 다르면 `@JsonProperty("input_tokens")`로 연결함
- `@JsonIgnoreProperties(ignoreUnknown = true)`: 우리가 정의하지 않은 필드가 와도 무시함. API에 필드가 추가돼도 깨지지 않음

### 4. Jev 요청 구조
- `assets`: 이미지를 이름(`photo-1`)으로 한 번 등록함 (base64)
- `state`: `{"photo": "photo-1"}`. 질문들이 함께 보는 내용이고, 여기서 이미지 이름을 참조함
- `questions`: 질문 이름 → `{"type": "noul", "instructions": "..."}`
- 응답 `answers.<질문이름>.noul`: "예"일 확률 (0~1)

### 5. 외부 API 테스트 분리
- 실제 API 테스트는 돈이 들고, 인터넷이 필요하고, 결과가 매번 조금씩 다를 수 있음
- `@Tag("jev")`를 붙이고 기본 `test`에서는 빼둠. 필요할 때만 `jevTest`로 실행함
- `@SpringBootTest(classes = JevClient.class)`: 앱 전체가 아니라 JevClient만 띄워서 DB 없이 실행함

## 코드 흐름
- `./gradlew jevTest` → JevClient 생성 (`.env`의 키로 Authorization 헤더 준비) → 테스트가 400x400 PNG를 그림 → `askYesNo()`가 이미지를 base64로 바꿔 JSON을 만듦 → `POST /v1/systemone` (+ `TypeSafe-Danger-Zone-Images: true`) → 응답 JSON을 `JevResponse`로 변환 → 확률 검증

## 오늘 겪은 것
- 첫 호출에서 403이 옴. 응답 본문이 비어 있어서 원인을 알 수 없었음
- 키 값은 출력하지 않고 키의 **상태**(비었는지, 길이)만 출력해 확인함. 결과는 `.env`의 키가 비어 있었음
- 공식 문서에는 키가 없으면 401이라고 되어 있지만 실제로는 403이 옴 → 문서와 실제가 다를 수 있으니 직접 확인해야 함
- 그래서 키가 비어 있으면 요청 전에 이유를 알려주는 검사를 추가함

---

# 4부 · 0단계: 영상 프레임 테스트와 진열대 전후 비교

## 오늘 한 것
- FFmpeg 설치. AI 생성 CCTV 영상(P1, 20초)에서 1초에 1장씩 프레임 20장을 뽑음
- `JevFrameLiveTest`: 프레임 20장에 질문 세트 v0을 보냄
  - 채점한 74개 판단이 모두 정답과 일치함
  - 가방에 넣는 순간은 손이 가려져 어느 프레임에도 보이지 않았음
- `JevShelfLiveTest`: 진열대 전후 사진 2장을 한 요청에 보내 "상품이 사라졌나"를 물음
- `JevClient`: 이미지 여러 장을 보낼 수 있게 확장함
- 설계 결정 001: 진열대 전후 비교를 판정 근거로 사용
- 결과 문서: `docs/test-data/2026-10-28_jev_영상_테스트.md`

## 바뀐 파일
- `backend/src/main/java/com/mujinjigi/domain/analysis/jev/JevClient.java`: `askYesNo(Map<String, byte[]>, ...)` 추가. 기존 한 장짜리 메서드는 이 메서드를 부름
- `backend/src/test/java/com/mujinjigi/domain/analysis/jev/QuestionSetV0.java`: 사진·프레임 테스트가 같이 쓰는 질문 세트
- `backend/src/test/java/com/mujinjigi/domain/analysis/jev/JevFrameLiveTest.java`: 장면 폴더별 프레임 테스트
- `backend/src/test/java/com/mujinjigi/domain/analysis/jev/JevShelfLiveTest.java`: 진열대 전후 비교 테스트
- `docs/portfolio/decisions/001_진열대_전후_비교를_판정_근거로_사용.md`: 설계 결정 기록
- `docs/test-data/촬영_목록.md`: 장면 목록과 AI 영상 생성 프롬프트

## 핵심 개념

### 1. 영상 → 프레임 (FFmpeg)
- 영상은 사진(프레임)을 빠르게 넘긴 것임. P1은 1초에 24장(24fps)
- `ffmpeg -i P1.mp4 -vf fps=1 P1_%02d.jpg`: 1초에 1장만 골라 저장함 (`-vf`는 영상 필터, `%02d`는 두 자리 번호)
- `ffprobe`: 영상 길이, 해상도, fps 같은 정보를 확인함
- 프레임 간격이 넓으면 0.5초짜리 동작을 놓칠 수 있음. 간격이 좁으면 Jev 호출 수(비용)가 늘어남 → 1단계에서 정할 트레이드오프

### 2. 한 요청에 이미지 여러 장 보내기
- `assets`에 이미지마다 참조 이름을 붙여 등록하고, `state`에서 `before`, `after`라는 이름으로 연결함
- 질문 문장 안에서 `` `before` ``, `` `after` ``처럼 이름으로 이미지를 가리킴
- 이미지 2장이라 입력 토큰도 약 2배임 (2,778 → 5,126)

### 3. 메서드 오버로딩과 위임
- 이름이 같은 `askYesNo`가 두 개 있음 (매개변수 타입이 다름 = 오버로딩)
- 한 장짜리 메서드는 `Map.of("photo", image)`로 감싸서 여러 장짜리 메서드를 부름(위임)
- 요청을 만드는 코드가 한 곳에만 있어서, 고칠 때 한 곳만 고치면 됨

### 4. 대조군(control)과 기준선(baseline)
- 같은 사진 두 장을 비교하는 경우를 함께 넣음 → "변화가 없을 때 Jev는 몇 점을 주는가"(기준선)
- 상품을 일부러 지운 사진을 넣음 → "변화가 있을 때 알아보는가"(대조군)
- 기준선과 비교해야 0.58이라는 숫자의 의미를 알 수 있음

### 5. 실험 데이터의 한계를 기록하기
- AI 영상은 상품을 가져가도 진열대를 그대로 그렸음. 결과가 나쁠 때 모델 탓인지 데이터 탓인지 나눠서 봐야 함
- 수정한 사진, AI 영상이라는 사실을 결과 문서에 밝혀 둠. 숫자가 무엇을 근거로 나왔는지 설명할 수 있어야 함

## 코드 흐름
- `./gradlew jevTest --tests '*JevShelfLiveTest'` → `before`, `after` 파일을 읽음 → `askYesNo(Map, "jpg", 질문)` → `state = {before: image-before, after: image-after}`, `assets = {image-before: ..., image-after: ...}` → `POST /v1/systemone` → missing/same 확률 출력

---

# 5부 · 1단계 시작: 영상 업로드 API (직접 작성)

## 오늘 한 것
- 진행 방식 변경: Claude Code가 가이드를 쓰고, 기능 코드는 직접 작성함 → `docs/guide/`
- 가이드 01을 따라 영상 업로드 API를 직접 작성함
  - `POST /api/videos`: 영상을 올리면 디스크에 UUID 이름으로 저장하고, DB `videos` 테이블에 정보를 저장함
  - `GET /api/videos/{id}`: 영상 정보 조회
- 테스트 4개 통과. 실제 서버(`bootRun`)에 P1.mp4(6MB)를 올려 `201 Created`, `Location: /api/videos/2`를 확인함
- 0단계 판단은 보류하고, 1단계 중 Jev 결과와 상관없는 항목부터 착수함 (handoff.md 진행 메모)

## 바뀐 파일
- `backend/src/main/java/com/mujinjigi/domain/video/entity/Video.java`: 영상 엔티티 (videos 테이블)
- `backend/src/main/java/com/mujinjigi/domain/video/repository/VideoRepository.java`: JpaRepository
- `backend/src/main/java/com/mujinjigi/domain/video/dto/VideoResponse.java`: 응답 DTO
- `backend/src/main/java/com/mujinjigi/domain/video/service/VideoFileStorage.java`: 디스크 저장
- `backend/src/main/java/com/mujinjigi/domain/video/service/VideoService.java`: 검사, 저장, 조회
- `backend/src/main/java/com/mujinjigi/domain/video/controller/VideoController.java`: API
- `backend/src/test/java/com/mujinjigi/domain/video/controller/VideoControllerTest.java`: MockMvc 테스트
- `backend/src/main/resources/application.properties`: 업로드 크기 500MB, 저장 폴더 (Claude Code가 수정)
- `docs/guide/README.md`, `docs/guide/01_영상_업로드_API.md`: 가이드

## 핵심 개념
- 개념 설명은 `docs/guide/01_영상_업로드_API.md` 1장 참고 (multipart, 계층 구조, UUID 파일 이름, 엔티티와 DTO, 상태 코드, @Transactional, MockMvc)

## 리뷰에서 지적된 점
1. **IntelliJ 빠른 수정이 엉뚱한 import를 넣음**
   - `extension` 변수를 만드는 줄이 빠지자 `import static jdk.internal.jrtfs...AttrID.extension`이 자동으로 추가됨
   - 빨간 줄이 생기면 먼저 가이드와 비교해 빠진 줄을 찾음. `jdk.internal`, `sun.`, `com.sun.`으로 시작하는 import 제안은 거의 항상 잘못된 것임
2. **`@Service` 누락**: 없으면 Spring이 이 클래스를 Bean으로 만들지 않아서 Controller가 주입받을 수 없음
3. **Location 주소의 `/` 누락** (`"/api/videos" + id` → `/api/videos2`)
   - 테스트는 통과했음. 테스트가 Location 헤더가 "있는지"만 보고 값은 보지 않았기 때문임
   - 테스트 통과 ≠ 버그 없음. 값까지 확인하는 테스트가 필요함 (아래 숙제)
4. **실행 폴더(Working directory) 문제**
   - IntelliJ가 저장소 루트에서 실행해서 `../.env`를 못 찾음 → DB 사용자 이름이 `${DB_USERNAME}` 글자 그대로 들어가 로그인 실패
   - 설정의 상대 경로(`../.env`, `../uploads/videos`)는 실행 폴더 기준임. IntelliJ 실행 설정의 Working directory를 `backend`로 바꿔야 함
5. 사소한 것: `try{` 띄어쓰기(`Ctrl+Alt+L`로 정리), 공개 저장소 주석의 "ㅇㅇ" 같은 표현 정리

## 숙제
- `VideoControllerTest`에서 Location 값까지 확인하도록 바꿔보기
  ```java
  .andExpect(header().string("Location", org.hamcrest.Matchers.matchesPattern("/api/videos/\\d+")))
  ```
  - 슬래시를 일부러 다시 빼고 테스트가 실패하는지 확인하면, 테스트가 버그를 잡는다는 것을 직접 볼 수 있음
- 실제 서버 확인에서 `/api/videos/1`은 404였음. 테스트에서 롤백해도 DB 번호표(시퀀스)는 되돌아가지 않아서 1번이 비어 있기 때문임 (정상). 올린 영상은 2번

## 스스로 답해볼 질문
면접에서 나올 만하거나 이 프로젝트의 핵심인 것만 추림.

1. 비밀값(API 키)이 이미 커밋돼 push까지 되었다면 어떻게 처리해야 하는가
2. DB 연결을 요청마다 새로 만들지 않고 연결 풀(HikariCP)을 쓰는 이유는 무엇인가
3. 행동 장면 대신 "진열대에서 사라진 상품 vs 결제 내역"을 최종 판정 기준으로 둔 이유는 무엇인가. 이 방식의 약점은 무엇인가
4. `@Transactional`이 있는데도 업로드한 영상 파일이 고아로 남을 수 있는 이유는 무엇인가. 어떻게 막을 수 있는가
5. 테스트가 모두 통과했는데 Location 버그가 남아 있던 이유는 무엇인가. 좋은 테스트는 무엇을 확인해야 하는가
