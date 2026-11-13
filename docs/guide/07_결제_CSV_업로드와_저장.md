# 가이드 07 · 결제 CSV 업로드와 저장

> **한 줄 요약**: 키오스크 **결제 내역 CSV**를 올리면 한 줄씩 **검사(`PaymentCsvParser`)**해서 틀린 줄이 하나라도 있으면 몇 번째 줄이 왜 틀렸는지 알려 주고, 모두 맞으면 **업로드 한 건(`payment_uploads`)과 결제 기록 여러 줄(`payment_records`)로 DB에 저장**해 조회 API로 꺼내 보는 기능 구현임

## 목표
- 2단계 첫 기능. 나중에 판정 로직이 "방문 시간 안에 이 상품이 결제됐나"를 확인할 **결제 쪽 데이터**를 만듦
- 금액·수량은 `long`(원 단위)으로 저장함 (handoff 5장 2단계)
- CSV 형식에 결제·취소 구분 칸을 지금 정해 둠. 취소 처리는 3단계에서 함
- 틀린 CSV는 **한 줄도 저장하지 않음**. 절반만 저장된 결제 내역으로 대조하면 정상 손님이 미결제로 잡힘

## 완료 기준
- `.\gradlew.bat test` 통과 (새 테스트 12개 포함, 전체 43개)
- 서버에서 `docs/test-data/payments/sample_kiosk.csv`를 올리면 201과 요약(4줄, 결제 합계 6,800원)이 오고, 기록 조회가 시각 순으로 나옴
- 틀린 CSV를 올리면 400과 함께 `"detail": "2번째 줄: ..."`이 응답에 보임

## 바뀌는 API
| 요청 | 응답 |
|---|---|
| `POST /api/payments` (form-data, 필드 `file`) | `201` + 업로드 요약, `Location: /api/payments/{id}`. 빈 파일·CSV 아님·틀린 줄 `400` |
| `GET /api/payments/{uploadId}` | `200` + 업로드 요약. 없으면 `404` |
| `GET /api/payments/{uploadId}/records` | `200` + 결제 기록 목록 (결제 시각 순, 같은 시각이면 CSV 순서). 없으면 `404` |
| 모든 오류 응답 (기존 API 포함) | 형식이 Problem Details로 바뀜. `detail`에 이유가 들어감 (1-7) |

응답 예:
```json
{ "id": 2, "originalName": "sample_kiosk.csv", "recordCount": 4, "totalAmount": 6800,
  "uploadedAt": "2026-11-09T15:47:35.9511366" }
```
```json
[ { "id": 5, "paidAt": "2026-11-09T14:03:21", "transactionId": "T0001", "productCode": "P-CHOCO",
    "productName": "초코바", "quantity": 2, "unitPrice": 1500, "amount": 3000, "type": "PAY" }, ... ]
```
```json
{ "title": "Bad Request", "status": 400,
  "detail": "2번째 줄: amount가 quantity × unit_price와 다름 (12000 ≠ 10 × 1500)", "instance": "/api/payments" }
```

---

## 1. 개념 먼저

### 1-1. CSV 형식을 우리가 정함
- 키오스크 회사마다 CSV 형식이 다름. 실제 매장 CSV를 받기 전이라 **이 프로젝트의 표준 형식**을 먼저 정하고, 나중에 다른 형식은 이 형식으로 바꿔 넣는 방식으로 감
- 헤더는 영어 소문자로 둠. 엑셀이나 다른 프로그램에서 한글 헤더가 깨지는 일을 피함

| 칸 | 예 | 뜻 | 왜 필요한가 |
|---|---|---|---|
| `paid_at` | `2026-11-09 14:03:21` | 결제 시각 (키오스크 시계) | 방문 시간과 대조함. CCTV 시계와 다를 수 있음 (시각 맞추기는 판정 로직) |
| `transaction_id` | `T0002` | 거래 번호 | 한 번 결제에 상품이 여러 개면 같은 번호가 여러 줄 |
| `product_code` | `P-COLA` | 상품 코드 | **진열대 칸과 결제를 잇는 열쇠**. 이름은 바뀌어도 코드는 그대로임 |
| `product_name` | `콜라` | 상품 이름 | 화면 표시용 |
| `quantity` | `1` | 수량 | 개수 비교 (2단계 판정) |
| `unit_price` | `2000` | 단가 (원) | |
| `amount` | `2000` | 금액 (원) = 수량 × 단가 | 검사용. 다르면 CSV가 깨진 것 |
| `type` | `PAY` / `CANCEL` | 결제 / 취소 | 형식만 지금 정함. 취소를 결제에서 빼는 처리는 3단계 |

### 1-2. 테이블 두 개: 업로드 한 건과 결제 기록 여러 줄
```
payment_uploads (CSV 파일 하나)          payment_records (CSV 한 줄)
┌────┬──────────────┬───────┐            ┌────┬───────────┬─────────────────────┬─────────┬ ...
│ id │ original_name│ total │  1 ── N    │ id │ upload_id │ paid_at             │ product │
│  2 │ sample.csv   │ 6800  │ ◀───────── │  5 │     2     │ 2026-11-09 14:03:21 │ P-CHOCO │
└────┴──────────────┴───────┘            │  6 │     2     │ 2026-11-09 14:05:02 │ P-COLA  │
```
- 영상(`videos`)과 프레임(`video_frames`)의 관계와 같음 (가이드 02의 `@ManyToOne`)
- 업로드를 따로 두는 이유: 같은 CSV를 두 번 올렸는지, 언제 무엇을 올렸는지 알 수 있고, 잘못 올린 CSV를 통째로 지울 단위가 생김
- 업로드에 줄 수와 결제 합계를 같이 저장함. 화면에서 목록을 보여 줄 때 기록 전체를 다시 더하지 않으려는 것임

### 1-3. 읽기(파서)를 서비스에서 떼어 냄
| 클래스 | 하는 일 | Spring·DB |
|---|---|---|
| `PaymentCsvParser` | 글자 → 검사된 줄 목록(`PaymentCsvRow`). 틀리면 예외 | 필요 없음 |
| `PaymentService` | 파일 받기 → 파서 호출 → 저장 | 필요함 |
- 검사 규칙(헤더, 칸 수, 날짜 형식, 금액 = 수량 × 단가)이 가장 자주 틀리는 곳이라, **Spring을 띄우지 않는 빠른 단위 테스트**로 경우를 많이 확인함 (`PaymentCsvParserTest` 7개)
- `PaymentCsvRow`는 "검사를 마친 한 줄"임. 엔티티와 따로 둔 이유: 엔티티(`PaymentRecord`)는 업로드에 속해야 만들 수 있는데, 읽는 단계에서는 아직 업로드가 없음
- 엔티티가 `PaymentCsvRow`를 직접 받지 않고 값을 하나씩 받게 함. **엔티티(아래 층)가 서비스(위 층) 클래스를 알면 의존 방향이 거꾸로 됨**

### 1-4. 전부 저장하거나, 하나도 저장하지 않거나
- 순서: ① 모든 줄을 검사함 → ② 하나라도 틀리면 400으로 끝냄 (DB에 아무것도 안 씀) → ③ 다 맞으면 저장함
- 저장은 `@Transactional` 안에서 함. 업로드는 저장됐는데 기록 저장 중에 오류가 나면 업로드도 같이 취소됨
- 왜 중요한가: 100줄 중 50줄만 저장되면 나머지 50줄의 결제는 "결제 안 함"으로 보여서, **결제한 손님이 미결제 의심으로 잡힘**
- 테스트 `틀린_줄이_있으면_400과_줄_번호를_돌려주고_아무것도_저장하지_않는다`가 이걸 확인함. 첫 줄은 맞았지만 저장되지 않아야 함

### 1-5. 금액은 `long`, 원 단위
- 원화는 소수점이 없음. `double`은 `0.1`을 정확히 담지 못해서 합계·비교에서 `6799.999999...` 같은 오차가 생길 수 있음
- `long`은 약 922경까지 담음. 수량 × 단가가 넘칠 일은 없음
- 검사: `amount != quantity * unitPrice`이면 받지 않음. 할인 행이 생기면 이 규칙을 바꿔야 함 (5장 한계 3)

### 1-6. CSV를 읽을 때 걸리는 세 가지
| 문제 | 어떻게 생기나 | 처리 |
|---|---|---|
| **BOM** | 엑셀에서 "CSV UTF-8"로 저장하면 파일 맨 앞에 보이지 않는 글자 `﻿`가 붙음. 그대로면 헤더가 `﻿paid_at,...`이 되어 "헤더가 다름"이 남 | 맨 앞이 BOM이면 잘라 냄 |
| **줄바꿈** | 윈도우는 `\r\n`, 리눅스·맥은 `\n` | `split("\\R")`: 정규식 `\R`은 모든 종류의 줄바꿈과 맞음 |
| **끝의 빈 칸** | `"a,b,".split(",")`는 `[a, b]` 2칸이 됨 (끝 빈 칸을 버림) | `split(",", -1)`: 빈 칸도 셈 → `[a, b, ""]` 3칸이라 "칸 수가 다름"으로 잡힘 |

- 오류 메시지에 **몇 번째 줄인지** 넣음. 줄 번호는 헤더를 1번째 줄로 셈 (엑셀에서 보는 줄 번호와 같음)

### 1-7. 오류 이유를 응답에 보여 주기 (Problem Details)
- 테스트(MockMvc)는 다 통과했는데, **실제 서버에 틀린 CSV를 올리니 응답에 이유가 없었음**:
  ```json
  {"timestamp":"...","status":400,"error":"Bad Request","path":"/api/payments"}
  ```
- 원인: Spring Boot는 기본 설정에서 오류 메시지를 응답에 넣지 않음 (서버 내부 정보가 새는 것을 막으려는 것임). MockMvc 테스트는 서버 안쪽에서 이유를 꺼내 봐서 이 차이가 안 보였음. 1일차 Location 버그처럼 **테스트가 통과해도 실제 응답은 다를 수 있다**는 예임
- 해결: `spring.mvc.problemdetails.enabled=true`
  - 오류 응답을 표준 형식(RFC 9457 Problem Details)으로 보냄. `ResponseStatusException`에 우리가 넣은 이유가 `detail`에 들어감
  - 예상하지 못한 오류(500)의 내부 메시지는 여전히 숨김. "이유를 보여 줄 오류"를 우리가 고른 셈임
  - 기존 API의 404·409·502 응답도 같은 형식이 되고 이유가 보임 (예: `"detail": "영상이 없음: 999999"`)
- 그래서 테스트도 `jsonPath("$.detail")`로 **응답 본문**을 확인함

### 1-8. 라이브러리 대신 직접 읽는 이유
- CSV 라이브러리(Apache Commons CSV 등)를 쓰면 따옴표로 감싼 칸(`"과자, 대용량"`)도 읽을 수 있음
- 이번에는 직접 `split`으로 읽음: 형식을 우리가 정했고, 상품 이름에 쉼표를 쓰지 않는다는 규칙만 지키면 충분함. 검사 규칙이 코드에 다 보여서 공부하기 좋음
- 따옴표 칸이 필요해지면(실제 키오스크 CSV) 파서만 바꾸면 됨. 서비스·엔티티는 그대로임 (1-3에서 떼어 둔 이유)

---

## 2. 미리 바꿔둔 설정
| 파일 | 바꾼 것 | 이유 |
|---|---|---|
| `backend/src/main/resources/application.properties` | `spring.mvc.problemdetails.enabled=true` 추가 | 1-7. 400 응답에 몇 번째 줄이 왜 틀렸는지 보이게 함 |
| `docs/test-data/payments/sample_kiosk.csv` (새 파일) | 결제 3줄 + 취소 1줄 샘플 | 4-2 실제 서버 확인용. 같은 거래(T0002)에 상품 2줄 |

`application.properties`에 추가된 부분:
```properties
spring.jpa.open-in-view=false

# ===== ★ 추가 (가이드 07) 시작 =====
# 오류 응답을 RFC 9457 Problem Details 형식으로 보냄. ResponseStatusException에 넣은 이유가 detail 칸에 들어감
# 기본 설정은 이유를 숨겨서, CSV의 몇 번째 줄이 틀렸는지 사용자가 알 수 없었음 (가이드 07)
spring.mvc.problemdetails.enabled=true
# ===== ★ 추가 (가이드 07) 끝 =====
```

---

## 3. 코드 작성
모두 새 파일이고 빈 파일을 만들어 둠 (`package` 줄과 빈 선언만 있음). 전부 새로 치는 코드라 `★ 추가` 표시는 없음

| 순서 | 파일 (`domain/payment/` 아래) | 역할 |
|---|---|---|
| 3-1 | `entity/PaymentType.java` | 결제·취소 enum |
| 3-2 | `entity/PaymentUpload.java` | 업로드 한 건 엔티티 |
| 3-3 | `entity/PaymentRecord.java` | 결제 기록 한 줄 엔티티 |
| 3-4 | `service/PaymentCsvRow.java` | 검사를 마친 한 줄 (record) |
| 3-5 | `service/PaymentCsvParser.java` | CSV 읽기와 검사 |
| 3-6 | `repository/PaymentUploadRepository.java`, `PaymentRecordRepository.java` | 저장·조회 |
| 3-7 | `dto/PaymentUploadResponse.java`, `PaymentRecordResponse.java` | 응답 형식 |
| 3-8 | `service/PaymentService.java` | 업로드·조회 |
| 3-9 | `controller/PaymentController.java` | API |
| 3-10 | 테스트 `service/PaymentCsvParserTest.java` | 파서 단위 테스트 7개 |
| 3-11 | 테스트 `controller/PaymentControllerTest.java` | API 테스트 5개 |

빈 파일의 선언 종류(`class`, `enum`, `interface`, `record`)는 아래 코드와 같게 만들어 둠

### 3-1. `entity/PaymentType.java`

```java
package com.mujinjigi.domain.payment.entity;

/**
 * 2단계 · 07 (결제 CSV 업로드와 저장)
 * 결제 기록이 결제인지 취소인지 나타내는 값. CSV의 type 칸에 이 이름 그대로 적힘
 * 예: PAY → 결제, CANCEL → 취소. 취소를 결제 수량에서 빼는 처리는 3단계에서 함
 */
public enum PaymentType {

    PAY,     // 결제
    CANCEL   // 취소·환불

}
```
- `MeasurementStep`(가이드 05)과 같은 enum. DB에는 `@Enumerated(EnumType.STRING)`으로 이름(`PAY`)을 저장함

### 3-2. `entity/PaymentUpload.java`

```java
package com.mujinjigi.domain.payment.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 2단계 · 07 (결제 CSV 업로드와 저장)
 * 결제 CSV 파일 하나를 올린 기록을 DB(payment_uploads 테이블)에 저장하는 엔티티. 결제 기록 여러 줄을 묶고 요약을 같이 저장함
 * 예: sample_kiosk.csv → 4줄, 결제 합계 6,800원
 */
@Entity
@Table(name = "payment_uploads")
public class PaymentUpload {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 사용자가 올린 파일 이름 (화면에 보여줄 용도). CSV는 내용을 DB에 저장하므로 파일은 디스크에 남기지 않음
    @Column(nullable = false)
    private String originalName;

    @Column(nullable = false)
    private int recordCount;

    // 결제(PAY) 행 금액의 합계(원). 취소 행은 더하지 않음
    @Column(nullable = false)
    private long totalAmount;

    @Column(nullable = false)
    private LocalDateTime uploadedAt;

    protected PaymentUpload() {
    }

    public PaymentUpload(String originalName, int recordCount, long totalAmount) {
        this.originalName = originalName;
        this.recordCount = recordCount;
        this.totalAmount = totalAmount;
        this.uploadedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getOriginalName() {
        return originalName;
    }

    public int getRecordCount() {
        return recordCount;
    }

    public long getTotalAmount() {
        return totalAmount;
    }

    public LocalDateTime getUploadedAt() {
        return uploadedAt;
    }
}
```
- CSV 파일 자체는 저장하지 않음. 내용을 모두 DB에 옮기므로 파일은 필요 없음 (영상과 다른 점)

### 3-3. `entity/PaymentRecord.java`

```java
package com.mujinjigi.domain.payment.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * 2단계 · 07 (결제 CSV 업로드와 저장)
 * 키오스크 결제 CSV의 한 줄을 DB(payment_records 테이블)에 저장하는 엔티티
 * 예: 2026-11-09 14:03:21, T0001, P-CHOCO 초코바 2개 × 1,500원 = 3,000원, PAY
 * 금액·수량은 원 단위 long으로 저장함 (double은 0.1 같은 값을 정확히 못 담아 합계에 오차가 생김)
 */
@Entity
@Table(name = "payment_records")
public class PaymentRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 여러 결제 기록이 업로드 하나에 속함
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "upload_id")
    private PaymentUpload upload;

    // 키오스크 시계 기준 결제 시각. CCTV 시계와 다를 수 있음 (시각 맞추기는 판정 로직에서 함)
    @Column(nullable = false)
    private LocalDateTime paidAt;

    // 키오스크 거래 번호. 한 번 결제에 상품이 여러 개면 같은 번호가 여러 줄에 나옴
    @Column(nullable = false)
    private String transactionId;

    // 상품 코드. 진열대 칸과 결제를 이을 때 이 값을 씀 (상품 이름은 바뀔 수 있음)
    @Column(nullable = false)
    private String productCode;

    @Column(nullable = false)
    private String productName;

    @Column(nullable = false)
    private long quantity;

    @Column(nullable = false)
    private long unitPrice;

    @Column(nullable = false)
    private long amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentType type;

    protected PaymentRecord() {
    }

    public PaymentRecord(PaymentUpload upload, LocalDateTime paidAt, String transactionId, String productCode,
                         String productName, long quantity, long unitPrice, long amount, PaymentType type) {
        this.upload = upload;
        this.paidAt = paidAt;
        this.transactionId = transactionId;
        this.productCode = productCode;
        this.productName = productName;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.amount = amount;
        this.type = type;
    }

    public Long getId() {
        return id;
    }

    public LocalDateTime getPaidAt() {
        return paidAt;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public String getProductCode() {
        return productCode;
    }

    public String getProductName() {
        return productName;
    }

    public long getQuantity() {
        return quantity;
    }

    public long getUnitPrice() {
        return unitPrice;
    }

    public long getAmount() {
        return amount;
    }

    public PaymentType getType() {
        return type;
    }
}
```
- 생성자가 값을 하나씩 받음 (1-3: 엔티티가 서비스의 `PaymentCsvRow`를 모르게 함)
- getter에 `getUpload()`가 없음. 지금은 기록에서 업로드로 거슬러 갈 일이 없어서 만들지 않음 (가이드 06에서 안 쓰는 getter가 커버리지 빈 곳으로 남았던 것과 같은 이유)

### 3-4. `service/PaymentCsvRow.java`

```java
package com.mujinjigi.domain.payment.service;

import java.time.LocalDateTime;

import com.mujinjigi.domain.payment.entity.PaymentType;

/**
 * 2단계 · 07 (결제 CSV 업로드와 저장)
 * CSV 한 줄을 읽고 검사까지 마친 값. DB에 저장하기 전 단계라 아직 엔티티(PaymentRecord)가 아님
 * 예: (2026-11-09T14:03:21, "T0001", "P-CHOCO", "초코바", 2, 1500, 3000, PAY)
 */
public record PaymentCsvRow(
        LocalDateTime paidAt,
        String transactionId,
        String productCode,
        String productName,
        long quantity,
        long unitPrice,
        long amount,
        PaymentType type) {
}
```

### 3-5. `service/PaymentCsvParser.java`

```java
package com.mujinjigi.domain.payment.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.mujinjigi.domain.payment.entity.PaymentType;

/**
 * 2단계 · 07 (결제 CSV 업로드와 저장)
 * 결제 CSV 글자를 한 줄씩 읽고 검사해서 PaymentCsvRow 목록으로 바꾸는 클래스. 틀린 줄이 있으면 몇 번째 줄이 왜 틀렸는지 알려 주고 멈춤
 * 예: "2026-11-09 14:03:21,T0001,P-CHOCO,초코바,2,1500,3000,PAY" → PaymentCsvRow(수량 2, 단가 1500, 금액 3000, PAY)
 * 예: amount 칸이 quantity × unit_price와 다르면 → "2번째 줄: amount가 quantity × unit_price와 다름"
 * DB·Spring 없이 동작해서 단위 테스트로 바로 확인함
 */
@Component
public class PaymentCsvParser {

    public static final String HEADER = "paid_at,transaction_id,product_code,product_name,quantity,unit_price,amount,type";

    private static final int COLUMN_COUNT = 8;
    private static final DateTimeFormatter PAID_AT_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    // 엑셀에서 "CSV UTF-8"로 저장하면 파일 맨 앞에 보이지 않는 글자(BOM)가 붙음. 그대로 두면 헤더가 달라 보임
    private static final String BOM = "﻿";

    public List<PaymentCsvRow> parse(String content) {
        if (content.startsWith(BOM)) {
            content = content.substring(1);
        }
        // \R: 줄바꿈 종류(윈도우 \r\n, 리눅스 \n)와 상관없이 줄을 나눔
        String[] lines = content.split("\\R");

        List<PaymentCsvRow> rows = new ArrayList<>();
        boolean headerChecked = false;
        for (int i = 0; i < lines.length; i++) {
            int lineNumber = i + 1;
            String line = lines[i].strip();
            if (line.isEmpty()) {
                continue;
            }
            if (!headerChecked) {
                if (!line.equals(HEADER)) {
                    throw new IllegalArgumentException(lineNumber + "번째 줄: 헤더가 다름. 필요한 헤더: " + HEADER);
                }
                headerChecked = true;
                continue;
            }
            rows.add(parseLine(line, lineNumber));
        }

        if (rows.isEmpty()) {
            throw new IllegalArgumentException("결제 기록이 한 줄도 없음");
        }
        return rows;
    }

    private PaymentCsvRow parseLine(String line, int lineNumber) {
        // -1: 끝에 빈 칸이 있어도 버리지 않고 칸 수에 셈 ("a,b," → 3칸)
        String[] columns = line.split(",", -1);
        if (columns.length != COLUMN_COUNT) {
            throw error(lineNumber, "칸이 " + COLUMN_COUNT + "개여야 함 (" + columns.length + "개)");
        }

        LocalDateTime paidAt = parsePaidAt(columns[0].strip(), lineNumber);
        String transactionId = required(columns[1], "transaction_id", lineNumber);
        String productCode = required(columns[2], "product_code", lineNumber);
        String productName = required(columns[3], "product_name", lineNumber);
        long quantity = parseNumber(columns[4], "quantity", lineNumber);
        long unitPrice = parseNumber(columns[5], "unit_price", lineNumber);
        long amount = parseNumber(columns[6], "amount", lineNumber);
        PaymentType type = parseType(columns[7].strip(), lineNumber);

        if (quantity < 1) {
            throw error(lineNumber, "quantity는 1 이상이어야 함 (" + quantity + ")");
        }
        if (unitPrice < 0) {
            throw error(lineNumber, "unit_price는 0 이상이어야 함 (" + unitPrice + ")");
        }
        // 금액이 수량 × 단가와 다르면 CSV가 깨졌거나 다른 형식임. 대조 결과를 믿을 수 없으므로 받지 않음
        if (amount != quantity * unitPrice) {
            throw error(lineNumber, "amount가 quantity × unit_price와 다름 (" + amount + " ≠ " + quantity + " × " + unitPrice + ")");
        }

        return new PaymentCsvRow(paidAt, transactionId, productCode, productName, quantity, unitPrice, amount, type);
    }

    private LocalDateTime parsePaidAt(String value, int lineNumber) {
        try {
            return LocalDateTime.parse(value, PAID_AT_FORMAT);
        } catch (DateTimeParseException e) {
            throw error(lineNumber, "paid_at 형식은 yyyy-MM-dd HH:mm:ss 여야 함 (" + value + ")");
        }
    }

    private long parseNumber(String value, String name, int lineNumber) {
        try {
            return Long.parseLong(value.strip());
        } catch (NumberFormatException e) {
            throw error(lineNumber, name + "는 정수여야 함 (" + value + ")");
        }
    }

    private PaymentType parseType(String value, int lineNumber) {
        try {
            return PaymentType.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw error(lineNumber, "type은 PAY 또는 CANCEL이어야 함 (" + value + ")");
        }
    }

    private String required(String value, String name, int lineNumber) {
        String stripped = value.strip();
        if (stripped.isEmpty()) {
            throw error(lineNumber, name + "가 비어 있음");
        }
        return stripped;
    }

    private IllegalArgumentException error(int lineNumber, String message) {
        return new IllegalArgumentException(lineNumber + "번째 줄: " + message);
    }
}
```
- `HEADER`를 `public`으로 둔 이유: 테스트에서 같은 헤더 문자열을 다시 적지 않고 가져다 씀
- `headerChecked`: 빈 줄을 건너뛰고 **처음 나온 글자 줄**을 헤더로 봄
- 칸 검사 순서: 칸 수 → 칸별 형식 → 칸 사이 관계(금액 = 수량 × 단가). 앞에서 틀리면 뒤는 보지 않음
- `error()`가 예외를 **만들기만** 하고 던지지 않음. 부르는 쪽에서 `throw error(...)`로 던져서, 읽는 사람이 "여기서 끝난다"는 것을 바로 알 수 있음
- 예외로 `IllegalArgumentException`(잘못된 입력)을 씀. HTTP를 모르는 클래스라 400 변환은 서비스가 함

### 3-6. `repository/PaymentUploadRepository.java`, `repository/PaymentRecordRepository.java`

```java
package com.mujinjigi.domain.payment.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mujinjigi.domain.payment.entity.PaymentUpload;

/**
 * 2단계 · 07 (결제 CSV 업로드와 저장)
 * payment_uploads 테이블에서 업로드 기록을 저장·조회하는 저장소
 * 예: findById(2) → 업로드 2번의 요약. save, findById 같은 기본 메서드는 JpaRepository가 만들어 줌
 */
public interface PaymentUploadRepository extends JpaRepository<PaymentUpload, Long> {
}
```

```java
package com.mujinjigi.domain.payment.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mujinjigi.domain.payment.entity.PaymentRecord;

/**
 * 2단계 · 07 (결제 CSV 업로드와 저장)
 * payment_records 테이블에서 결제 기록을 저장·조회하는 저장소
 * 예: findByUploadIdOrderByPaidAtAscIdAsc(2) → 업로드 2번의 기록을 결제 시각 순으로 (같은 시각이면 CSV 순서)
 */
public interface PaymentRecordRepository extends JpaRepository<PaymentRecord, Long> {

    List<PaymentRecord> findByUploadIdOrderByPaidAtAscIdAsc(Long uploadId);
}
```
- `findByUploadIdOrderByPaidAtAscIdAsc`: `upload.id`로 찾고 → 결제 시각 순 → 시각이 같으면 `id` 순(= 저장한 순서 = CSV 순서). 같은 거래의 상품 여러 줄은 시각이 같아서 두 번째 정렬 기준이 없으면 순서가 매번 달라질 수 있음

### 3-7. `dto/PaymentUploadResponse.java`, `dto/PaymentRecordResponse.java`

```java
package com.mujinjigi.domain.payment.dto;

import java.time.LocalDateTime;

import com.mujinjigi.domain.payment.entity.PaymentUpload;

/**
 * 2단계 · 07 (결제 CSV 업로드와 저장)
 * 결제 CSV 업로드 API가 돌려주는 요약 JSON의 모양
 * 예: {"id": 2, "originalName": "sample_kiosk.csv", "recordCount": 4, "totalAmount": 6800, ...}
 */
public record PaymentUploadResponse(Long id, String originalName, int recordCount, long totalAmount,
                                    LocalDateTime uploadedAt) {

    public static PaymentUploadResponse from(PaymentUpload upload) {
        return new PaymentUploadResponse(
                upload.getId(),
                upload.getOriginalName(),
                upload.getRecordCount(),
                upload.getTotalAmount(),
                upload.getUploadedAt());
    }
}
```

```java
package com.mujinjigi.domain.payment.dto;

import java.time.LocalDateTime;

import com.mujinjigi.domain.payment.entity.PaymentRecord;
import com.mujinjigi.domain.payment.entity.PaymentType;

/**
 * 2단계 · 07 (결제 CSV 업로드와 저장)
 * 결제 기록 API가 돌려주는 JSON 한 칸의 모양
 * 예: {"paidAt": "2026-11-09T14:03:21", "productCode": "P-CHOCO", "productName": "초코바", "quantity": 2, "unitPrice": 1500, "amount": 3000, "type": "PAY", ...}
 */
public record PaymentRecordResponse(Long id, LocalDateTime paidAt, String transactionId, String productCode,
                                    String productName, long quantity, long unitPrice, long amount,
                                    PaymentType type) {

    public static PaymentRecordResponse from(PaymentRecord record) {
        return new PaymentRecordResponse(
                record.getId(),
                record.getPaidAt(),
                record.getTransactionId(),
                record.getProductCode(),
                record.getProductName(),
                record.getQuantity(),
                record.getUnitPrice(),
                record.getAmount(),
                record.getType());
    }
}
```

### 3-8. `service/PaymentService.java`

```java
package com.mujinjigi.domain.payment.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.mujinjigi.domain.payment.dto.PaymentRecordResponse;
import com.mujinjigi.domain.payment.dto.PaymentUploadResponse;
import com.mujinjigi.domain.payment.entity.PaymentRecord;
import com.mujinjigi.domain.payment.entity.PaymentType;
import com.mujinjigi.domain.payment.entity.PaymentUpload;
import com.mujinjigi.domain.payment.repository.PaymentRecordRepository;
import com.mujinjigi.domain.payment.repository.PaymentUploadRepository;

/**
 * 2단계 · 07 (결제 CSV 업로드와 저장)
 * 결제 CSV 파일을 받아 PaymentCsvParser로 검사하고, 업로드 한 건과 결제 기록 여러 줄로 저장하는 클래스. 저장한 것을 다시 조회도 함
 * 흐름: 빈 파일·확장자 검사 → 줄마다 검사 → 결제 합계 계산 → 업로드·기록 저장 (틀린 줄이 하나라도 있으면 아무것도 저장 안 함)
 */
@Service
public class PaymentService {

    private final PaymentUploadRepository paymentUploadRepository;
    private final PaymentRecordRepository paymentRecordRepository;
    private final PaymentCsvParser paymentCsvParser;

    public PaymentService(PaymentUploadRepository paymentUploadRepository,
                          PaymentRecordRepository paymentRecordRepository,
                          PaymentCsvParser paymentCsvParser) {
        this.paymentUploadRepository = paymentUploadRepository;
        this.paymentRecordRepository = paymentRecordRepository;
        this.paymentCsvParser = paymentCsvParser;
    }

    // 한 줄이라도 틀리면 아무것도 저장하지 않음. 검사를 저장보다 먼저 끝내고, 저장은 한 트랜잭션으로 묶음
    @Transactional
    public PaymentUploadResponse upload(MultipartFile file) {
        if (file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "빈 파일은 올릴 수 없음");
        }
        String name = file.getOriginalFilename();
        if (name == null || !name.toLowerCase(Locale.ROOT).endsWith(".csv")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CSV 파일만 올릴 수 있음");
        }

        List<PaymentCsvRow> rows;
        try {
            rows = paymentCsvParser.parse(new String(file.getBytes(), StandardCharsets.UTF_8));
        } catch (IllegalArgumentException e) {
            // 몇 번째 줄이 왜 틀렸는지 그대로 돌려줌
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (IOException e) {
            throw new UncheckedIOException("CSV 파일을 읽지 못함: " + name, e);
        }

        long totalAmount = rows.stream()
                .filter(row -> row.type() == PaymentType.PAY)
                .mapToLong(PaymentCsvRow::amount)
                .sum();
        PaymentUpload upload = paymentUploadRepository.save(new PaymentUpload(name, rows.size(), totalAmount));
        paymentRecordRepository.saveAll(rows.stream()
                .map(row -> new PaymentRecord(upload, row.paidAt(), row.transactionId(), row.productCode(),
                        row.productName(), row.quantity(), row.unitPrice(), row.amount(), row.type()))
                .toList());
        return PaymentUploadResponse.from(upload);
    }

    @Transactional(readOnly = true)
    public PaymentUploadResponse find(Long uploadId) {
        return PaymentUploadResponse.from(findUpload(uploadId));
    }

    @Transactional(readOnly = true)
    public List<PaymentRecordResponse> findRecords(Long uploadId) {
        findUpload(uploadId);
        return paymentRecordRepository.findByUploadIdOrderByPaidAtAscIdAsc(uploadId).stream()
                .map(PaymentRecordResponse::from)
                .toList();
    }

    private PaymentUpload findUpload(Long uploadId) {
        return paymentUploadRepository.findById(uploadId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "결제 업로드가 없음: " + uploadId));
    }
}
```
- 파일 이름 검사는 확장자만 봄 (`.csv`, 대소문자 무시). 내용 검사는 파서가 함
- `file.getBytes()`를 UTF-8로 읽음. 엑셀의 "CSV(쉼표로 분리)" 저장은 한글이 CP949(EUC-KR)라 깨짐 → 5장 한계 2
- `totalAmount`: 결제(`PAY`) 줄만 더함. 스트림 `filter` → `mapToLong` → `sum`
- `saveAll`: 여러 엔티티를 한 번에 저장 요청함 (내부적으로는 한 줄씩 INSERT)
- `findRecords()`에서 `findUpload()`를 먼저 부르는 이유: 업로드가 없으면 빈 목록 `[]`(200) 대신 404를 돌려주려는 것임 (`FrameService.findAll`과 같음)

### 3-9. `controller/PaymentController.java`

```java
package com.mujinjigi.domain.payment.controller;

import java.net.URI;
import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.mujinjigi.domain.payment.dto.PaymentRecordResponse;
import com.mujinjigi.domain.payment.dto.PaymentUploadResponse;
import com.mujinjigi.domain.payment.service.PaymentService;

/**
 * 2단계 · 07 (결제 CSV 업로드와 저장)
 * 결제 CSV API 요청을 받는 클래스
 * POST /api/payments → CSV 업로드·저장 / GET /api/payments/{id} → 업로드 요약 / GET /api/payments/{id}/records → 결제 기록 목록
 */
@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    // POST /api/payments (form-data, 필드 이름 file) → 201 Created + 업로드 요약, Location 헤더
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<PaymentUploadResponse> upload(@RequestParam("file") MultipartFile file) {
        PaymentUploadResponse response = paymentService.upload(file);
        return ResponseEntity.created(URI.create("/api/payments/" + response.id())).body(response);
    }

    // GET /api/payments/{uploadId} → 업로드 요약, 없으면 404
    @GetMapping("/{uploadId}")
    public PaymentUploadResponse find(@PathVariable Long uploadId) {
        return paymentService.find(uploadId);
    }

    // GET /api/payments/{uploadId}/records → 결제 기록 목록 (결제 시각 순), 없으면 404
    @GetMapping("/{uploadId}/records")
    public List<PaymentRecordResponse> findRecords(@PathVariable Long uploadId) {
        return paymentService.findRecords(uploadId);
    }
}
```
- `Location`이 가리키는 `GET /api/payments/{id}`를 같이 만듦. 돌려준 주소가 실제로 열려야 함 (1일차 Location 버그, 가이드 06 조회 테스트)

### 3-10. 테스트: `service/PaymentCsvParserTest.java`

```java
package com.mujinjigi.domain.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.mujinjigi.domain.payment.entity.PaymentType;

/**
 * 2단계 · 07 (결제 CSV 업로드와 저장)
 * PaymentCsvParser가 CSV를 제대로 읽고 틀린 줄을 잡아내는지 확인하는 테스트. Spring·DB 없이 글자를 넣고 결과만 확인함
 * 예: 금액이 수량 × 단가와 다른 줄을 넣으면 → "N번째 줄: ..." 예외가 나는지
 */
class PaymentCsvParserTest {

    private static final String HEADER = PaymentCsvParser.HEADER;

    PaymentCsvParser parser = new PaymentCsvParser();

    @Test
    void 줄마다_결제_기록을_만든다() {
        String csv = HEADER + "\n"
                + "2026-11-09 14:03:21,T0001,P-CHOCO,초코바,2,1500,3000,PAY\n"
                + "2026-11-09 14:05:02,T0002,P-COLA,콜라,1,2000,2000,CANCEL\n";

        List<PaymentCsvRow> rows = parser.parse(csv);

        assertThat(rows).hasSize(2);
        PaymentCsvRow first = rows.get(0);
        assertThat(first.paidAt()).isEqualTo(LocalDateTime.of(2026, 11, 9, 14, 3, 21));
        assertThat(first.transactionId()).isEqualTo("T0001");
        assertThat(first.productCode()).isEqualTo("P-CHOCO");
        assertThat(first.productName()).isEqualTo("초코바");
        assertThat(first.quantity()).isEqualTo(2);
        assertThat(first.unitPrice()).isEqualTo(1500);
        assertThat(first.amount()).isEqualTo(3000);
        assertThat(first.type()).isEqualTo(PaymentType.PAY);
        assertThat(rows.get(1).type()).isEqualTo(PaymentType.CANCEL);
    }

    @Test
    void 엑셀이_붙인_BOM과_윈도우_줄바꿈과_빈_줄은_넘어간다() {
        String csv = "﻿" + HEADER + "\r\n"
                + "\r\n"
                + "2026-11-09 14:03:21,T0001,P-CHOCO,초코바,1,1500,1500,PAY\r\n"
                + "\r\n";

        List<PaymentCsvRow> rows = parser.parse(csv);

        assertThat(rows).hasSize(1);
    }

    @Test
    void 헤더가_다르면_예외를_던진다() {
        String csv = "시각,거래번호,상품코드,상품명,수량,단가,금액,구분\n"
                + "2026-11-09 14:03:21,T0001,P-CHOCO,초코바,1,1500,1500,PAY\n";

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> parser.parse(csv));

        assertThat(e.getMessage()).contains("1번째 줄").contains("헤더");
    }

    @Test
    void 결제_기록이_없으면_예외를_던진다() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> parser.parse(HEADER + "\n"));

        assertThat(e.getMessage()).contains("한 줄도 없음");
    }

    @Test
    void 칸_수가_다르면_줄_번호와_함께_예외를_던진다() {
        // 상품 이름에 쉼표가 들어가면 칸이 하나 늘어남 (따옴표로 감싼 칸은 지원하지 않음)
        String csv = HEADER + "\n"
                + "2026-11-09 14:03:21,T0001,P-CHOCO,초코바,1,1500,1500,PAY\n"
                + "2026-11-09 14:05:02,T0002,P-SNACK,과자,대용량,1,3000,3000,PAY\n";

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> parser.parse(csv));

        assertThat(e.getMessage()).contains("3번째 줄").contains("9개");
    }

    @Test
    void 금액이_수량_곱하기_단가와_다르면_예외를_던진다() {
        String csv = HEADER + "\n"
                + "2026-11-09 14:03:21,T0001,P-CHOCO,초코바,10,1500,12000,PAY\n";

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> parser.parse(csv));

        assertThat(e.getMessage()).contains("2번째 줄").contains("12000 ≠ 10 × 1500");
    }

    @Test
    void 칸_값이_틀리면_어느_칸이_틀렸는지_알려준다() {
        assertThat(errorOf("2026/11/09 14:03:21,T0001,P-CHOCO,초코바,1,1500,1500,PAY")).contains("paid_at");
        assertThat(errorOf("2026-11-09 14:03:21, ,P-CHOCO,초코바,1,1500,1500,PAY")).contains("transaction_id가 비어 있음");
        assertThat(errorOf("2026-11-09 14:03:21,T0001,P-CHOCO,초코바,한개,1500,1500,PAY")).contains("quantity는 정수");
        assertThat(errorOf("2026-11-09 14:03:21,T0001,P-CHOCO,초코바,0,1500,0,PAY")).contains("quantity는 1 이상");
        assertThat(errorOf("2026-11-09 14:03:21,T0001,P-CHOCO,초코바,1,-1500,-1500,PAY")).contains("unit_price는 0 이상");
        assertThat(errorOf("2026-11-09 14:03:21,T0001,P-CHOCO,초코바,1,1500,1500,REFUND")).contains("PAY 또는 CANCEL");
    }

    // 헤더 + 한 줄짜리 CSV를 읽혀서 예외 메시지를 돌려줌
    private String errorOf(String line) {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> parser.parse(HEADER + "\n" + line + "\n"));
        return e.getMessage();
    }
}
```
- `@SpringBootTest`가 없음. `new PaymentCsvParser()`로 직접 만들어서 Spring 없이 바로 돎 (`FrameSelectorTest`와 같은 방식)
- `칸_값이_틀리면_어느_칸이_틀렸는지_알려준다`: 비슷한 경우 6개를 도우미 메서드 `errorOf()`로 묶음. 테스트 메서드를 6개로 나눠도 되지만, 모두 "한 줄짜리 CSV → 메시지 확인"이라 한곳에 모음

### 3-11. 테스트: `controller/PaymentControllerTest.java`

```java
package com.mujinjigi.domain.payment.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.mujinjigi.domain.payment.repository.PaymentRecordRepository;
import com.mujinjigi.domain.payment.repository.PaymentUploadRepository;
import com.mujinjigi.domain.payment.service.PaymentCsvParser;

/**
 * 2단계 · 07 (결제 CSV 업로드와 저장)
 * 결제 CSV API를 실제 DB에 저장해 가며 불러 보는 테스트. 테스트가 끝나면 저장한 내용을 되돌림
 * 확인하는 것: 업로드 요약·기록 조회, 틀린 줄 400과 줄 번호(아무것도 저장 안 됨), CSV 아닌 파일·빈 파일 400, 없는 업로드 404
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PaymentControllerTest {

    // 같은 거래(T0002)에 상품 두 줄, 취소 한 줄. 일부러 시각 순서를 섞어 둠
    private static final String CSV = PaymentCsvParser.HEADER + "\n"
            + "2026-11-09 14:05:02,T0002,P-COLA,콜라,1,2000,2000,PAY\n"
            + "2026-11-09 14:03:21,T0001,P-CHOCO,초코바,2,1500,3000,PAY\n"
            + "2026-11-09 14:05:02,T0002,P-CHIPS,감자칩,1,1800,1800,PAY\n"
            + "2026-11-09 14:10:40,T0003,P-COLA,콜라,1,2000,2000,CANCEL\n";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    PaymentUploadRepository paymentUploadRepository;

    @Autowired
    PaymentRecordRepository paymentRecordRepository;

    @Test
    void CSV를_올리면_201과_요약을_돌려주고_기록을_시각순으로_조회한다() throws Exception {
        String location = mockMvc.perform(multipart("/api/payments").file(csvFile("kiosk_1109.csv", CSV)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.originalName").value("kiosk_1109.csv"))
                .andExpect(jsonPath("$.recordCount").value(4))
                // 결제 3줄 합계. 취소 줄(2,000원)은 더하지 않음
                .andExpect(jsonPath("$.totalAmount").value(3000 + 2000 + 1800))
                .andReturn().getResponse().getHeader("Location");

        mockMvc.perform(get(location))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recordCount").value(4));

        mockMvc.perform(get(location + "/records"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[0].transactionId").value("T0001"))
                .andExpect(jsonPath("$[0].paidAt").value("2026-11-09T14:03:21"))
                .andExpect(jsonPath("$[0].amount").value(3000))
                // 같은 시각이면 CSV에 적힌 순서대로
                .andExpect(jsonPath("$[1].productCode").value("P-COLA"))
                .andExpect(jsonPath("$[2].productCode").value("P-CHIPS"))
                .andExpect(jsonPath("$[3].type").value("CANCEL"));
    }

    @Test
    void 틀린_줄이_있으면_400과_줄_번호를_돌려주고_아무것도_저장하지_않는다() throws Exception {
        long uploadsBefore = paymentUploadRepository.count();
        long recordsBefore = paymentRecordRepository.count();
        String broken = PaymentCsvParser.HEADER + "\n"
                + "2026-11-09 14:03:21,T0001,P-CHOCO,초코바,2,1500,3000,PAY\n"
                + "2026-11-09 14:05:02,T0002,P-COLA,콜라,0,2000,0,PAY\n";

        mockMvc.perform(multipart("/api/payments").file(csvFile("broken.csv", broken)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("3번째 줄")));

        // 첫 줄은 맞았지만 저장되지 않음
        assertThat(paymentUploadRepository.count()).isEqualTo(uploadsBefore);
        assertThat(paymentRecordRepository.count()).isEqualTo(recordsBefore);
    }

    @Test
    void CSV가_아닌_파일은_400을_돌려준다() throws Exception {
        mockMvc.perform(multipart("/api/payments").file(csvFile("payments.xlsx", CSV)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 빈_파일은_400을_돌려준다() throws Exception {
        mockMvc.perform(multipart("/api/payments").file(csvFile("empty.csv", "")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 없는_업로드를_조회하면_404를_돌려준다() throws Exception {
        mockMvc.perform(get("/api/payments/{id}", 999_999)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/payments/{id}/records", 999_999)).andExpect(status().isNotFound());
    }

    private MockMultipartFile csvFile(String name, String content) {
        return new MockMultipartFile("file", name, "text/csv", content.getBytes(StandardCharsets.UTF_8));
    }
}
```
- CSV의 줄 순서를 일부러 섞어 둠 (14:05 → 14:03). 조회가 시각 순으로 정렬되는지 보려는 것임
- `count()`로 저장 전후 개수를 비교해 "아무것도 저장하지 않음"을 확인함 (1-4)
- `jsonPath("$.detail")`: Problem Details 응답 본문의 이유 (1-7)
- `containsString`: Hamcrest의 "이 글자를 포함하는가" 조건. `import static org.hamcrest.Matchers.containsString;`

---

## 4. 확인

### 4-1. 테스트
Docker PostgreSQL이 켜져 있어야 함 (`docs/guide/README.md` 참고).
```powershell
cd backend
$env:JAVA_HOME = "$env:USERPROFILE\.jdks\ms-21.0.12"
.\gradlew.bat clean test
```
- `BUILD SUCCESSFUL`이면 통과임 (전체 43개)
- 커버리지 보고서에서 `com.mujinjigi.domain.payment` 패키지를 봄. Claude Code가 잰 값: `PaymentCsvParser` 줄 53 / 53·분기 22 / 22, 다른 클래스도 빠진 줄 없음. `PaymentService`만 파일 이름 `null` 분기 1개와 `catch (IOException)` 2줄이 빠짐 (가이드 06 4-2와 같은 이유)
- 전체: 줄 554 / 574 (96.5%), 분기 84 / 88 (95.5%)

### 4-2. 실제 서버로 확인
서버 실행 (backend 폴더에서):
```powershell
.\gradlew.bat bootRun
```
다른 터미널에서 (저장소 루트에서). 업로드 응답의 `id`로 바꿔서 부름:
```powershell
curl.exe -i -F "file=@docs/test-data/payments/sample_kiosk.csv" http://localhost:8080/api/payments
curl.exe -s http://localhost:8080/api/payments/2/records
```
- 201, `recordCount` 4, `totalAmount` 6800이 오면 됨. 기록은 초코바 → 콜라 → 감자칩 → 콜라(CANCEL) 순서임
- 틀린 CSV도 올려 봄. `sample_kiosk.csv`를 복사해 아무 줄의 `amount`를 바꾼 뒤 올리면 `"detail": "N번째 줄: amount가 ..."`이 보여야 함
- 엑셀로 샘플을 열어 "CSV UTF-8"로 다시 저장한 파일도 올려 봄 (BOM이 붙음). 그대로 201이면 1-6의 BOM 처리가 동작한 것임

---

## 5. 알려진 한계 (면접에서 물어볼 만한 부분)
1. **따옴표로 감싼 칸을 못 읽음**: 상품 이름에 쉼표가 있으면 칸 수 오류가 남 (1-8). 실제 키오스크 CSV를 받으면 CSV 라이브러리로 바꿀 수 있음
2. **UTF-8만 읽음**: 엑셀 기본 "CSV(쉼표로 분리)" 저장은 한글이 CP949라 깨짐. 헤더는 영어라 통과하지만 상품 이름이 깨진 채 저장됨. 인코딩을 자동으로 알아내거나 업로드 화면에서 고르게 하는 방법이 있음
3. **할인 행을 못 받음**: 금액 = 수량 × 단가 검사 때문임. 할인이 있는 키오스크라면 `discount` 칸을 더하고 검사식을 바꿔야 함
4. **같은 CSV를 두 번 올려도 막지 않음**: 결제 기록이 두 배가 되면 미결제를 놓칠 수 있음 (결제가 있는 것처럼 보임). 파일 내용의 해시나 거래 번호 중복으로 막는 방법이 있음. 대조 단계에서 다시 봄
5. **큰 CSV는 한꺼번에 메모리에 올림**: `getBytes()`로 파일 전체를 읽음. 매장 하루치(수백~수천 줄)는 문제없지만, 몇 달치를 한 번에 올리면 줄 단위로 읽는(스트리밍) 방식이 필요함
6. **우리 양식만 읽음**: 키오스크 회사마다 칸 이름·날짜 형식·인코딩이 다름. 점주가 손으로 고치지 않게, 3단계 초반에 양식별 변환기(헤더로 자동 인식 → 우리 표준 `PaymentCsvRow`로 바꿈)를 넣고, 웹 업로드 화면 때 칸 매핑 설정을 더함 (handoff 5장 3단계). 파서를 서비스와 떼어 둔 덕분에 그때도 서비스·엔티티는 그대로임

---

## 6. 다 했으면
"가이드 07 다 했어"라고 알려 주세요. 이렇게 진행합니다.
1. 작성한 코드 리뷰
2. `clean test`로 테스트와 커버리지 확인
3. 통과하면 학습 노트, WBS 갱신, 기능 단위 커밋

## 스스로 답해볼 질문
1. 금액을 `double`이 아니라 `long`(원 단위)으로 저장한 이유는 무엇인가 (면접 질문)
2. CSV에 틀린 줄이 하나라도 있으면 전체를 저장하지 않는 이유는 무엇인가. 일부만 저장되면 이 서비스에서 어떤 문제가 생기는가
3. 파서를 서비스에서 떼어 Spring 없이 동작하게 만든 이유는 무엇인가
4. MockMvc 테스트는 통과했는데 실제 서버 응답에는 오류 이유가 없었던 이유는 무엇인가. Problem Details를 켜도 500 오류의 내부 메시지가 숨겨지는 것이 왜 중요한가
5. 결제 기록과 진열대 칸을 이을 때 상품 이름이 아니라 상품 코드를 쓰는 이유는 무엇인가
