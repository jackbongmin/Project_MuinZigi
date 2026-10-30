# 가이드 01 · 영상 업로드 API

> **한 줄 요약**: 점주가 올린 **영상 파일**을 **디스크**에 저장하고 그 정보를 **DB(`videos`)**에 기록해, 이후 단계가 영상 id 하나로 녹화본을 찾을 수 있게 하는 기능 구현임

## 목표
- 점주가 녹화본을 올리면 서버가 파일을 디스크에 저장하고, 영상 정보를 DB에 기록함
- 올린 영상의 정보를 id로 조회할 수 있음
- 1단계 첫 항목 "영상 업로드 API". 다음 가이드(프레임 추출)가 여기서 저장한 영상을 씀

## 완료 기준
- `.\gradlew.bat test` 통과 (새 테스트 4개 포함)
- 서버를 띄우고 실제 영상(`docs/test-data/videos/P1.mp4`)을 올리면 `201 Created`가 오고, `uploads/videos/`에 파일이 생김

## 만들 API
| 요청 | 설명 | 성공 | 실패 |
|---|---|---|---|
| `POST /api/videos` | form-data로 `file` 필드에 영상을 담아 보냄 | 201 + 영상 정보 + `Location` 헤더 | 빈 파일·영상 아님: 400 |
| `GET /api/videos/{id}` | 영상 정보 조회 | 200 + 영상 정보 | 없는 id: 404 |

응답 예시:
```json
{"id": 1, "originalName": "P1.mp4", "sizeBytes": 6088503, "uploadedAt": "2026-10-28T16:04:56.037"}
```

---

## 1. 개념 먼저

### 1-1. 파일 업로드 = multipart/form-data
- 보통 API 요청 본문은 JSON 한 덩어리임. 파일은 이진 데이터라 JSON에 그대로 넣기 어려움
- **multipart/form-data**: 요청 본문을 여러 칸(part)으로 나눠 보내는 형식. 칸마다 이름이 있고, 파일 칸에는 파일 이름과 내용이 들어감
- Spring은 `file`이라는 칸을 `MultipartFile` 객체로 바꿔줌. `getOriginalFilename()`, `getSize()`, `isEmpty()`, `transferTo(경로)`를 씀
- Spring의 기본 업로드 크기 제한은 1MB라서, 영상을 받으려고 설정에서 500MB로 늘려 둠 (아래 "미리 바꿔둔 설정")

### 1-2. 계층 구조: Controller → Service → Repository
요청이 들어오면 아래 순서로 처리됨. 각 계층은 자기 일만 함.

| 계층 | 하는 일 | 이번 파일 |
|---|---|---|
| Controller | HTTP 요청을 받고 응답을 만듦. 상태 코드, 헤더 | `VideoController` |
| Service | 실제 규칙 처리. 검사, 저장 순서, 트랜잭션 | `VideoService`, `VideoFileStorage` |
| Repository | DB에 저장·조회 | `VideoRepository` |
| Entity | DB 테이블 한 줄에 대응하는 객체 | `Video` |
| DTO | API로 주고받는 데이터 형식 | `VideoResponse` |

- 이렇게 나누면 예를 들어 "파일 저장을 S3로 바꾸기"는 `VideoFileStorage`만 고치면 됨

### 1-3. 파일은 디스크에, DB에는 정보만
- 영상(수백 MB)을 DB에 넣으면 DB가 무거워지고 백업·조회가 느려짐
- 파일은 디스크(나중에 S3)에 두고, DB에는 "어떤 이름으로 어디에 저장했는지"만 저장함
- **저장 이름은 UUID**로 만듦 (`7eb72632-....mp4`)
  - 두 점주가 같은 이름(`cctv.mp4`)으로 올려도 덮어쓰지 않음
  - 사용자가 보낸 파일 이름을 경로에 그대로 쓰면 `../../` 같은 이름으로 다른 폴더에 파일을 쓰는 공격이 가능함. UUID를 쓰면 이 공격을 막을 수 있음
- 원래 이름(`originalName`)은 화면에 보여주려고 따로 저장함

### 1-4. JPA 엔티티
- `@Entity`: 이 클래스가 DB 테이블과 대응한다는 표시. `@Table(name = "videos")`로 테이블 이름을 정함
- `@Id @GeneratedValue(strategy = IDENTITY)`: 기본키. 값은 DB가 1, 2, 3… 순서로 매김
- `@Column(nullable = false)`: 이 칸은 비어 있으면 안 됨 (DB 제약조건)
- 기본 생성자가 꼭 있어야 함. JPA가 DB에서 읽은 값으로 객체를 만들 때 씀. 다른 코드에서 쓰지 않게 `protected`로 둠
- setter를 두지 않음. 값은 생성자에서만 정해서, 객체가 아무 데서나 바뀌지 않게 함
- 테이블은 `ddl-auto=update` 설정 때문에 서버가 뜰 때 자동으로 만들어짐

### 1-5. 엔티티와 DTO를 나누는 이유
- 엔티티를 API 응답으로 그대로 내보내면 DB 구조가 API에 묶임. 칸 하나를 추가해도 API 응답이 바뀜
- `storedName`(디스크 파일 이름) 같은 내부 정보가 밖으로 나감
- 그래서 응답 전용 `record VideoResponse`를 만들고 필요한 값만 담음

### 1-6. HTTP 상태 코드와 ResponseStatusException
- `201 Created`: 새로 만들어짐. `Location` 헤더에 새 자원의 주소(`/api/videos/1`)를 넣는 것이 관례임
- `400 Bad Request`: 요청이 잘못됨 (빈 파일, 영상이 아닌 파일)
- `404 Not Found`: 찾는 자원이 없음
- `throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "이유")`: 이 예외를 던지면 Spring이 해당 상태 코드로 응답함
  - 지금은 이 방식이 가장 단순함
  - 나중에 오류 응답 형식을 통일할 때 `@RestControllerAdvice`(전역 예외 처리)로 바꿀 예정

### 1-7. @Transactional
- 메서드 안의 DB 작업을 하나로 묶음. 중간에 예외가 나면 그 안의 DB 변경을 모두 되돌림(롤백)
- `readOnly = true`: 조회만 한다고 알려서 불필요한 변경 감지를 생략함
- **주의**: 디스크에 쓴 파일은 롤백되지 않음 → 아래 "알려진 한계" 참고

### 1-8. MockMvc 테스트
- 서버를 실제로 띄우지 않고 Controller에 가짜 HTTP 요청을 보내는 도구임
- `MockMultipartFile`로 가짜 파일을 만들어 업로드를 흉내 냄
- `jsonPath("$.originalName")`: 응답 JSON에서 값을 꺼내 확인함
- 테스트 클래스에 `@Transactional`을 붙이면, 테스트가 끝날 때 DB에 저장한 내용을 되돌림. 테스트를 여러 번 돌려도 DB가 쌓이지 않음

---

## 2. 미리 바꿔둔 설정 (Claude Code가 수정함)
`backend/src/main/resources/application.properties`에 아래를 추가해 둠. 직접 칠 필요 없음.

```properties
# 영상 업로드. 기본 제한(1MB)으로는 영상을 받을 수 없어서 늘림
spring.servlet.multipart.max-file-size=500MB
spring.servlet.multipart.max-request-size=500MB
# 업로드한 영상을 저장할 폴더 (backend/ 기준, 저장소 루트의 uploads/는 커밋되지 않음). 배포 때 S3로 바꿀 예정
app.storage.video-dir=../uploads/videos
```

- `max-file-size`: 파일 한 개의 최대 크기. `max-request-size`: 요청 전체의 최대 크기
- `app.storage.video-dir`: 우리가 직접 만든 설정 이름. 코드에서 `@Value("${app.storage.video-dir}")`로 읽음
- `uploads/`는 이미 `.gitignore`에 있어서 커밋되지 않음

---

## 3. 코드 작성
아래 순서대로 만듦. 아래쪽 파일이 위쪽 파일을 쓰기 때문에, 순서대로 만들면 중간에 빨간 줄이 덜 생김.

모든 파일은 `backend/src/main/java/com/mujinjigi/domain/video/` 아래에 있음 (테스트만 `src/test/...`).

```
domain/video/
├── entity/Video.java
├── repository/VideoRepository.java
├── dto/VideoResponse.java
├── service/VideoFileStorage.java
├── service/VideoService.java
└── controller/VideoController.java
```

### 3-1. `entity/Video.java`
```java
package com.mujinjigi.domain.video.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 1단계 · 01 (영상 업로드 API)
 * 업로드한 녹화본 한 개. 파일 자체는 디스크에 두고, DB에는 파일을 찾을 정보만 저장함
 */
@Entity
@Table(name = "videos")
public class Video {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 사용자가 올린 파일 이름 (화면에 보여줄 용도)
    @Column(nullable = false)
    private String originalName;

    // 디스크에 저장한 파일 이름. 이름이 겹치지 않도록 UUID로 만듦
    @Column(nullable = false, unique = true)
    private String storedName;

    @Column(nullable = false)
    private long sizeBytes;

    @Column(nullable = false)
    private LocalDateTime uploadedAt;

    // JPA가 DB에서 읽어 객체를 만들 때 쓰는 기본 생성자. 다른 코드에서는 쓰지 않게 protected로 둠
    protected Video() {
    }

    public Video(String originalName, String storedName, long sizeBytes) {
        this.originalName = originalName;
        this.storedName = storedName;
        this.sizeBytes = sizeBytes;
        this.uploadedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getOriginalName() {
        return originalName;
    }

    public String getStoredName() {
        return storedName;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public LocalDateTime getUploadedAt() {
        return uploadedAt;
    }
}
```
- `import jakarta.persistence.*`: JPA 표준 어노테이션. 예전 자료의 `javax.persistence`는 옛 이름이라 쓰지 않음
- `Long id`는 대문자 `Long`임. 저장 전에는 id가 없어서 `null`이어야 하는데, 소문자 `long`은 `null`이 될 수 없음
- `uploadedAt`은 생성자에서 현재 시각으로 정함

### 3-2. `repository/VideoRepository.java`
```java
package com.mujinjigi.domain.video.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mujinjigi.domain.video.entity.Video;

/**
 * 1단계 · 01 (영상 업로드 API)
 * Video 저장·조회. save, findById 같은 기본 메서드는 JpaRepository가 만들어 줌
 */
public interface VideoRepository extends JpaRepository<Video, Long> {
}
```
- 인터페이스만 만들면 Spring Data JPA가 실행 시점에 구현 클래스를 자동으로 만들어 줌
- `JpaRepository<Video, Long>`: 다룰 엔티티 타입, 기본키 타입
- 쓸 수 있는 메서드: `save()`, `findById()`, `findAll()`, `deleteById()`, `count()` 등

### 3-3. `dto/VideoResponse.java`
```java
package com.mujinjigi.domain.video.dto;

import java.time.LocalDateTime;

import com.mujinjigi.domain.video.entity.Video;

/**
 * 1단계 · 01 (영상 업로드 API)
 * 영상 정보를 API로 돌려줄 때 쓰는 응답 형식. 엔티티를 그대로 내보내지 않고 필요한 값만 담음
 */
public record VideoResponse(Long id, String originalName, long sizeBytes, LocalDateTime uploadedAt) {

    public static VideoResponse from(Video video) {
        return new VideoResponse(
                video.getId(),
                video.getOriginalName(),
                video.getSizeBytes(),
                video.getUploadedAt());
    }
}
```
- `storedName`은 일부러 뺌 (내부 정보)
- `from()`: 엔티티를 DTO로 바꾸는 정적 메서드. 바꾸는 코드를 한 곳에 모아 둠

### 3-4. `service/VideoFileStorage.java`
```java
package com.mujinjigi.domain.video.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/**
 * 1단계 · 01 (영상 업로드 API)
 * 업로드한 영상 파일을 디스크에 저장함. 배포 때 S3로 바꾸면 이 클래스만 바뀌면 됨
 */
@Component
public class VideoFileStorage {

    private final Path videoDir;

    public VideoFileStorage(@Value("${app.storage.video-dir}") String videoDir) {
        this.videoDir = Path.of(videoDir).toAbsolutePath().normalize();
    }

    /**
     * @return 디스크에 저장한 파일 이름 (UUID.확장자)
     */
    public String save(MultipartFile file, String extension) {
        String storedName = UUID.randomUUID() + "." + extension;
        try {
            Files.createDirectories(videoDir);
            file.transferTo(videoDir.resolve(storedName));
        } catch (IOException e) {
            throw new UncheckedIOException("영상 파일 저장 실패: " + storedName, e);
        }
        return storedName;
    }
}
```
- `toAbsolutePath().normalize()`: `../uploads/videos`를 `C:\...\Project_MuinZigi\uploads\videos` 같은 절대 경로로 바꾸고 `..`을 정리함
- `Files.createDirectories()`: 폴더가 없으면 만듦 (있으면 아무것도 안 함)
- `transferTo(경로)`: 업로드된 파일 내용을 그 경로에 씀
- `IOException`은 반드시 처리해야 하는 예외(checked)임. 호출하는 쪽이 매번 try-catch를 쓰지 않게 `UncheckedIOException`으로 감싸서 던짐

### 3-5. `service/VideoService.java`
```java
package com.mujinjigi.domain.video.service;

import java.util.Locale;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.mujinjigi.domain.video.dto.VideoResponse;
import com.mujinjigi.domain.video.entity.Video;
import com.mujinjigi.domain.video.repository.VideoRepository;

/**
 * 1단계 · 01 (영상 업로드 API)
 * 영상 업로드와 조회
 */
@Service
public class VideoService {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("mp4", "mov", "avi", "mkv");

    private final VideoRepository videoRepository;
    private final VideoFileStorage videoFileStorage;

    public VideoService(VideoRepository videoRepository, VideoFileStorage videoFileStorage) {
        this.videoRepository = videoRepository;
        this.videoFileStorage = videoFileStorage;
    }

    @Transactional
    public VideoResponse upload(MultipartFile file) {
        if (file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "빈 파일은 올릴 수 없음");
        }

        String extension = extensionOf(file.getOriginalFilename());
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "영상 파일(mp4, mov, avi, mkv)만 올릴 수 있음");
        }

        String storedName = videoFileStorage.save(file, extension);
        Video video = videoRepository.save(new Video(file.getOriginalFilename(), storedName, file.getSize()));
        return VideoResponse.from(video);
    }

    @Transactional(readOnly = true)
    public VideoResponse find(Long id) {
        Video video = videoRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "영상이 없음: " + id));
        return VideoResponse.from(video);
    }

    // "P1.MP4" → "mp4". 점이 없으면 빈 문자열
    private String extensionOf(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    }
}
```
- **생성자 주입**: 필요한 부품(Repository, Storage)을 생성자로 받음. 생성자가 하나면 Spring이 알아서 넣어 줌(`@Autowired` 생략 가능)
- 검사 순서: 빈 파일 → 확장자 → 파일 저장 → DB 저장. 잘못된 요청은 디스크에 쓰기 전에 걸러냄
- `findById()`는 `Optional<Video>`를 돌려줌. 값이 없을 수 있다는 뜻이고, `orElseThrow()`로 없으면 404 예외를 던짐
- `toLowerCase(Locale.ROOT)`: `P1.MP4`도 받기 위해 소문자로 바꿈. `Locale.ROOT`는 실행 환경 언어에 따라 결과가 달라지지 않게 함

### 3-6. `controller/VideoController.java`
```java
package com.mujinjigi.domain.video.controller;

import java.net.URI;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.mujinjigi.domain.video.dto.VideoResponse;
import com.mujinjigi.domain.video.service.VideoService;

/**
 * 1단계 · 01 (영상 업로드 API)
 * 영상 업로드·조회 API
 */
@RestController
@RequestMapping("/api/videos")
public class VideoController {

    private final VideoService videoService;

    public VideoController(VideoService videoService) {
        this.videoService = videoService;
    }

    // POST /api/videos (form-data, 필드 이름 file) → 201 Created + 새 영상 주소(Location 헤더)
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<VideoResponse> upload(@RequestParam("file") MultipartFile file) {
        VideoResponse response = videoService.upload(file);
        return ResponseEntity.created(URI.create("/api/videos/" + response.id())).body(response);
    }

    // GET /api/videos/{id} → 200 OK, 없으면 404
    @GetMapping("/{id}")
    public VideoResponse find(@PathVariable Long id) {
        return videoService.find(id);
    }
}
```
- `@RestController`: 메서드가 돌려준 객체를 JSON으로 바꿔 응답함
- `@RequestMapping("/api/videos")`: 이 클래스의 모든 주소 앞에 붙는 공통 경로
- `consumes = MULTIPART_FORM_DATA_VALUE`: multipart 요청만 받음
- `@RequestParam("file")`: form-data의 `file` 칸을 꺼냄
- `ResponseEntity.created(주소).body(...)`: 상태 코드 201, `Location` 헤더, 본문을 한 번에 만듦
- `@PathVariable`: 주소의 `{id}` 자리 값을 꺼냄
- Controller는 검사나 저장을 직접 하지 않고 Service에 맡김

### 3-7. 테스트: `backend/src/test/java/com/mujinjigi/domain/video/controller/VideoControllerTest.java`
```java
package com.mujinjigi.domain.video.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * 1단계 · 01 (영상 업로드 API)
 * 영상 업로드 API 테스트. 서버를 실제로 띄우지 않고 MockMvc로 요청을 흉내 냄
 * DB는 Docker PostgreSQL을 쓰고, 테스트가 끝나면 @Transactional로 저장한 내용을 되돌림
 */
@SpringBootTest(properties = "app.storage.video-dir=build/test-uploads")
@AutoConfigureMockMvc
@Transactional
class VideoControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void 영상을_올리면_201과_영상_정보를_돌려준다() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "P1.mp4", "video/mp4", new byte[1024]);

        mockMvc.perform(multipart("/api/videos").file(file))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.originalName").value("P1.mp4"))
                .andExpect(jsonPath("$.sizeBytes").value(1024));
    }

    @Test
    void 영상이_아닌_파일은_400을_돌려준다() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "memo.txt", "text/plain", "hello".getBytes());

        mockMvc.perform(multipart("/api/videos").file(file))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 빈_파일은_400을_돌려준다() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "P1.mp4", "video/mp4", new byte[0]);

        mockMvc.perform(multipart("/api/videos").file(file))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 없는_영상을_조회하면_404를_돌려준다() throws Exception {
        mockMvc.perform(get("/api/videos/{id}", 999_999))
                .andExpect(status().isNotFound());
    }
}
```
- `@SpringBootTest(properties = ...)`: 앱 전체를 띄우되, 테스트에서는 저장 폴더를 `build/test-uploads`로 바꿈 (실제 업로드 폴더를 더럽히지 않음)
- `@AutoConfigureMockMvc`: MockMvc를 준비해 줌
  - Spring Boot 4부터 패키지가 `org.springframework.boot.webmvc.test.autoconfigure`로 바뀜. 옛 자료의 `org.springframework.boot.test.autoconfigure.web.servlet`은 안 됨
- `MockMultipartFile("file", "P1.mp4", "video/mp4", 내용)`: form-data 칸 이름, 파일 이름, 형식, 내용
- 테스트 메서드 이름을 한글로 쓰면 결과 화면에서 무엇을 확인하는지 바로 읽힘

---

## 4. 확인

### 4-1. 테스트
```powershell
cd backend
$env:JAVA_HOME = "$env:USERPROFILE\.jdks\ms-21.0.12"
.\gradlew.bat test
```
- `BUILD SUCCESSFUL`이 나오면 통과임
- 실패하면 `backend/build/reports/tests/test/index.html`을 브라우저로 열어 어떤 테스트가 왜 실패했는지 확인함

### 4-2. 실제 서버로 업로드
- **반드시 `backend` 폴더에서 실행함.** 설정의 `../.env`, `../uploads/videos`는 실행 폴더 기준 경로라서, 저장소 루트에서 실행하면 `.env`를 못 찾아 DB 로그인이 실패함 (`user "${DB_USERNAME}"` 오류)
- IntelliJ의 ▶ 버튼으로 실행할 때는 Edit Configurations → Working directory를 `...\Project_MuinZigi\backend`로 바꿔 둠
- 서버가 켜져 있는 동안 Gradle은 `80% EXECUTING`에서 멈춘 것처럼 보이는데 정상임 (서버가 꺼질 때까지 끝나지 않는 작업)

서버 실행 (backend 폴더에서, 터미널 하나를 계속 띄워 둠):
```powershell
.\gradlew.bat bootRun
```
`Started MujinjigiApplication`이 보이면 다른 터미널에서 (저장소 루트에서):
```powershell
curl.exe -i -F "file=@docs/test-data/videos/P1.mp4" http://localhost:8080/api/videos
curl.exe http://localhost:8080/api/videos/1
```
- PowerShell에서는 `curl` 대신 `curl.exe`라고 써야 함 (`curl`은 PowerShell의 다른 명령으로 연결되어 있음)
- `HTTP/1.1 201`, `Location: /api/videos/1`이 보이면 성공임
- 저장소 루트의 `uploads/videos/`에 UUID 이름의 mp4 파일이 생겼는지 확인함
- id가 1이 아닐 수 있음. 테스트에서 롤백해도 DB의 번호표는 되돌아가지 않기 때문임 (정상)

DB에 저장된 내용 확인 (저장소 루트에서):
```powershell
docker exec mujinjigi-postgres sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "select * from videos;"'
```

서버 끄기: bootRun 터미널에서 `Ctrl + C`

---

## 5. 알려진 한계 (면접에서 물어볼 만한 부분)
지금은 단순함을 우선해서 아래를 일부러 남겨 둠. 나중 단계에서 개선함.

1. **파일은 저장됐는데 DB 저장이 실패하면 파일이 고아로 남음**
   - `@Transactional`은 DB만 되돌리고 디스크 파일은 되돌리지 못함
   - 개선 방법: DB 저장 실패 시 파일 삭제, 또는 주기적으로 DB에 없는 파일을 정리
2. **확장자만 검사함**
   - `memo.txt`의 이름을 `memo.mp4`로 바꾸면 통과함
   - 개선 방법: 다음 가이드에서 FFprobe로 실제 영상인지, 길이가 얼마인지 확인함
3. **업로드 요청이 파일을 다 받을 때까지 기다림**
   - 지금은 저장까지만 해서 괜찮음
   - 분석까지 이 요청 안에서 하면 응답이 아주 늦어짐 → 2단계에서 분석을 비동기 작업(Job)으로 분리함 (성능 기록 후보: 업로드 응답 시간 전후 비교)
4. **오류 응답 형식이 Spring 기본값임**
   - 전역 예외 처리(`@RestControllerAdvice`)로 형식을 통일할 예정

---

## 6. 다 했으면
"가이드 01 다 했어"라고 알려 주세요. 이렇게 진행합니다.
1. 작성한 코드 리뷰 (가이드와 다른 점, 더 나은 방법)
2. 테스트 실행
3. 통과하면 학습 노트 작성과 기능 단위 커밋

## 스스로 답해볼 질문
1. 영상 파일을 DB에 넣지 않고 디스크에 저장한 이유는 무엇인가
2. 저장 파일 이름을 사용자가 보낸 이름 대신 UUID로 만든 이유 두 가지는 무엇인가
3. 엔티티를 그대로 응답으로 내보내면 어떤 문제가 생기는가
4. `@Transactional`이 있는데도 파일이 고아로 남을 수 있는 이유는 무엇인가 (면접 질문)
5. 201과 200의 차이, `Location` 헤더는 왜 넣는가
