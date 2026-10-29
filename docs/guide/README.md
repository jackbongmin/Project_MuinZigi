# 가이드 사용법

## 진행 방식
1. Claude Code가 이 폴더에 가이드를 씀. 가이드의 코드는 Claude Code가 먼저 직접 작성해 테스트까지 통과시킨 것임
2. 가이드를 보며 **기능 코드를 직접 작성함** (복사·붙여넣기보다 직접 치는 것을 권장)
3. 다 쳤으면 "다 했어"라고 알림 → Claude Code가 코드 리뷰와 테스트 실행
4. 테스트가 통과하면 기능 단위로 커밋함 (push는 직접)

- 설정 파일(build.gradle, application.properties 등)은 Claude Code가 미리 고쳐 두고, 가이드의 "미리 바꿔둔 설정"에 무엇을 왜 바꿨는지 적음
- 막히는 부분, 이해 안 되는 줄이 있으면 바로 질문함

## 테스트 실행 방법
이 PC는 PATH에 Java가 없어서 JAVA_HOME을 지정해야 함. PowerShell 기준 (backend 폴더에서):

```powershell
$env:JAVA_HOME = "$env:USERPROFILE\.jdks\ms-21.0.12"
.\gradlew.bat test
```

- IntelliJ에서 실행하면 프로젝트 SDK로 Java 21을 지정해 두면 됨
- 테스트와 서버 실행 전에 Docker PostgreSQL이 켜져 있어야 함 (저장소 루트에서)

```powershell
docker compose --env-file .env -f infra/docker-compose.yml up -d
```

## 가이드 목록
| 번호 | 가이드 | 상태 |
|---|---|---|
| 01 | [영상 업로드 API](01_영상_업로드_API.md) | 완료 (2026-10-28, 테스트 4개 통과, 실제 업로드 확인) |
| 02 | [프레임 추출과 고르기](02_프레임_추출과_고르기.md) | 완료 (2026-10-29, 테스트 13개 통과, 실제 서버에서 P1 16장 확인) |
