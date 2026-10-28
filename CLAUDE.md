# CLAUDE.md — 무인지기 (mujinjigi)

이 파일은 Claude Code가 작업마다 반드시 지킬 규칙만 정리함. 프로젝트 전체 맥락은 `docs/handoff.md` 참고.

## 프로젝트 한 줄 요약
- 무인매장 CCTV 녹화본과 키오스크 결제 내역(CSV)을 받아, Jev로 손님 행동을 판단하고 결제와 대조해 미결제 의심 장면을 찾아주는 서비스
- 백엔드 포트폴리오 프로젝트임. 웹(React)과 모바일 앱(React Native)이 같은 Spring 서버 API를 사용함

## 기술 스택 (변경 시 반드시 사용자에게 먼저 확인)
- 백엔드: Java 21, Spring Boot, Gradle, PostgreSQL
- 웹: React + Vite / 앱: React Native + Expo
- AI 판단: Jev 1.14 (이미지 입력), 프레임 추출: FFmpeg
- 배포: Docker

## 작업 방식
- 사용자는 매일 그날 추가된 기능과 코드를 직접 공부함. 코드는 설명 가능한 수준으로 단순하고 명확하게 작성함
- 새 개념이 들어가는 작업은 코드보다 개념 설명을 먼저 함
- **기능 코드(Java 등)는 사용자가 직접 작성함.** Claude Code는 `docs/guide/`에 가이드를 쓰고, 사용자가 다 치면 리뷰하고 테스트를 실행함
  - 가이드에는 파일별 전체 코드와 설명을 넣음. 가이드 코드는 Claude Code가 먼저 직접 작성해 테스트를 통과시킨 뒤 옮기고, 검증용 코드는 지움
  - 설정 파일(build.gradle, application.properties, docker-compose, .gitignore 등)은 Claude Code가 직접 수정하고, 가이드에 무엇을 왜 바꿨는지 설명함
  - 실험·측정용 테스트 코드(Jev 실제 호출, 성능 측정)는 Claude Code가 작성할 수 있음
- 작업을 마칠 때마다 `docs/study/YYYY-MM-DD.md`에 일일 학습 노트를 작성함 (양식은 handoff.md 9장). 개념 설명은 가이드에 있으므로 노트는 오늘 한 것, 리뷰에서 지적된 점, 스스로 답할 질문 중심으로 씀
- 커밋은 테스트가 끝난 뒤 기능 단위로 묶어서 함. push는 사용자가 직접 함
- 단계 진행은 `docs/handoff.md` 5장의 순서와 완료 기준을 따름. 완료 기준 통과 전에는 다음 단계로 넘어가지 않음

## 커밋 규칙
- 커밋 메시지는 한글로 작성함
- 접두사: `feat:` `fix:` `docs:` `add:` `perf:` `refactor:` `test:` `chore:`
- 예: `perf: 장면 변화 프레임만 Jev로 전송하도록 변경`
- **커밋 메시지와 PR 설명에 `Co-Authored-By: Claude`, `Generated with Claude Code` 등 Claude 관련 문구를 절대 넣지 않음**
- 커밋 작성자는 사용자 본인 계정임. git 사용자 설정을 변경하지 않음

## 보안 규칙
- `.env` 파일은 읽거나 수정하지 않음. 필요한 값은 `.env.example`에 키 이름만 추가하고 사용자에게 알림
- API 키, 비밀번호, JWT 비밀키를 코드·문서·로그·커밋에 넣지 않음
- Jev 호출은 서버에서만 함. 웹·앱 코드에 API 키를 넣지 않음
- 비밀번호는 BCrypt로 암호화해 저장함
- 영상, 업로드 파일, 추출 프레임은 저장소에 커밋하지 않음 (`.gitignore` 유지)

## 포트폴리오 기록 규칙 (중요)
- **성능 개선 작업 전에는 반드시 기준(Before)을 먼저 측정하고 기록함. 개선 후 같은 조건으로 다시 측정함**
- 기록 대상은 포트폴리오에 쓸 만한 것만: 숫자로 전후 비교가 되는 개선 / 원인 추적 과정이 있었던 문제 / 이유를 갖고 고른 설계 결정
- 오타, 단순 설정 실수, 설치 오류처럼 한 줄로 끝나는 문제는 기록하지 않음
- 기록 위치: `docs/portfolio/performance/`, `troubleshooting/`, `decisions/` (양식은 handoff.md 8장)
- 시각 자료:
  - 직접 만들 수 있는 것(측정 로그 그래프, 웹 화면 캡처, 터미널 측정 결과)은 직접 생성해 `docs/portfolio/assets/`에 저장함
  - 직접 볼 수 없는 화면(IDE 프로파일러, 휴대폰 앱, 클라우드 콘솔 등)은 "지금 이 화면을 캡처해서 `docs/portfolio/assets/<파일명>`으로 저장해 주세요"라고 사용자에게 요청함
  - 파일명: `YYYY-MM-DD_주제_before.png` / `YYYY-MM-DD_주제_after.png`
- 기록할 만한 상황이 생기면 작업 중에 바로 사용자에게 알리고 기록함. 나중으로 미루지 않음

## 문서 작성 문체
- 모든 기록 문서는 사용자가 직접 쓴 것처럼 "~함", "~임" 체로 작성함
