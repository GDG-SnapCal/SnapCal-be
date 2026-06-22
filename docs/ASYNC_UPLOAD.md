# 비동기 업로드 플로우

> 작성일: 2026-06-22  
> 관련 커밋: `feat: 사진 업로드 비동기 처리 (즉시 202 반환 + 폴링)`

---

## 변경 배경

기존 구조에서는 `POST /api/photos/upload` 가 동기로 실행되어, 사진 1장당 GPT API 호출(최대 30초)이 순차 처리되었음.
10장 업로드 시 최대 **5분 이상** 대기가 발생하고, 브라우저/앱 기본 타임아웃(60초)을 초과할 수 있는 구조적 문제가 있었음.

---

## 플로우 비교

### 변경 전 (동기)
```
POST /api/photos/upload
  ├─ [사진1] 스토리지 업로드 → GPT 분류 → pHash → DB 저장
  ├─ [사진2] 스토리지 업로드 → GPT 분류 → pHash → DB 저장
  └─ [사진N] ...
  → 모두 완료 후 200 OK + 분류 결과 반환  ← 최대 5분 대기
```

### 변경 후 (비동기)
```
POST /api/photos/upload
  ├─ [사진1] 스토리지 업로드 → DB 저장 (PROCESSING)
  ├─ [사진2] 스토리지 업로드 → DB 저장 (PROCESSING)
  └─ [사진N] ...
  → 즉시 202 Accepted + { uploadId, total }

  [백그라운드 - photoProcessingExecutor 스레드풀]
  ├─ [사진1] GPT 분류 → pHash → 선명도 → DB 업데이트 (PENDING)
  ├─ [사진2] GPT 분류 → pHash → 선명도 → DB 업데이트 (PENDING)  ← 병렬 처리
  └─ [사진N] ...

GET /api/photos/upload/{uploadId}/status  ← FE가 폴링
  → 진행 중: 202 + { status: "processing", total, completed }
  → 완료: 200 + { status: "done", classifications, duplicateGroups }
```

---

## 새 API 스펙

### 1. POST /api/photos/upload

**인증**: Bearer Token 필요  
**요청**: `multipart/form-data`, `photos` 키에 파일 배열

**응답**: `202 Accepted`
```json
{
  "success": true,
  "data": {
    "uploadId": "550e8400-e29b-41d4-a716-446655440000",
    "total": 5
  }
}
```

---

### 2. GET /api/photos/upload/{uploadId}/status

**인증**: Bearer Token 필요

**응답 — 처리 중**: `202 Accepted`
```json
{
  "success": true,
  "data": {
    "uploadId": "550e8400-e29b-41d4-a716-446655440000",
    "status": "processing",
    "total": 5,
    "completed": 2
  }
}
```

**응답 — 완료**: `200 OK`
```json
{
  "success": true,
  "data": {
    "uploadId": "550e8400-e29b-41d4-a716-446655440000",
    "status": "done",
    "total": 5,
    "completed": 5,
    "classifications": [
      {
        "photoId": "uuid",
        "url": "https://...",
        "takenAt": "2024-03-15",
        "category": "음식"
      }
    ],
    "duplicateGroups": [
      {
        "groupId": "uuid",
        "takenAt": "2024-03-15",
        "photos": [
          { "photoId": "uuid", "url": "https://...", "takenAt": "2024-03-15" }
        ],
        "aiRecommendedPhotoId": "uuid"
      }
    ]
  }
}
```
> `duplicateGroups`는 중복이 없으면 `null`.

---

## FE 연동 가이드

### 변경 전후 비교

| 항목 | 변경 전 | 변경 후 |
|---|---|---|
| 업로드 응답 코드 | `200 OK` | `202 Accepted` |
| 업로드 응답 바디 | 분류 결과 전체 즉시 포함 | `{ uploadId, total }` 만 반환 |
| 결과 수신 방식 | 업로드 응답에서 바로 파싱 | status API 폴링 후 파싱 |
| 이후 플로우 | 동일 | 동일 (변경 없음) |

### 폴링 구현 예시

```js
// 업로드
const uploadRes = await fetch('/api/photos/upload', {
  method: 'POST',
  headers: { Authorization: `Bearer ${token}` },
  body: formData,
});
const { data: { uploadId, total } } = await uploadRes.json();

// 폴링 (2초 간격)
const result = await pollStatus(uploadId, total);

async function pollStatus(uploadId, total) {
  while (true) {
    const res = await fetch(`/api/photos/upload/${uploadId}/status`, {
      headers: { Authorization: `Bearer ${token}` },
    });
    const { data } = await res.json();

    // 프로그레스바 업데이트
    setProgress(data.completed / data.total);

    if (data.status === 'done') return data;
    await new Promise(r => setTimeout(r, 2000));
  }
}

// 이후 플로우는 기존과 동일
if (result.duplicateGroups) {
  // 중복 선택 화면 표시 후 POST /api/photos/duplicates/select
}
// POST /api/calendar/save (uploadId 사용)
```

### 권장 폴링 간격

| 상황 | 권장 간격 |
|---|---|
| 일반 (1~5장) | 1초 |
| 다수 (6~20장) | 2초 |
| 타임아웃 처리 | 5분 초과 시 에러 처리 |

---

## 내부 구조

### 새로 추가된 파일

| 파일 | 역할 |
|---|---|
| `config/AsyncConfig.java` | `@EnableAsync` + `photoProcessingExecutor` 스레드풀 설정 (core=4, max=10) |
| `service/PhotoProcessingService.java` | `@Async` 백그라운드 처리 전담. GPT 분류 → pHash → 선명도 → PENDING 전환 |
| `dto/response/UploadInitiatedResponse.java` | 202 응답 DTO `{ uploadId, total }` |
| `dto/response/UploadStatusResponse.java` | 상태 폴링 응답 DTO |

### 변경된 파일

| 파일 | 변경 내용 |
|---|---|
| `domain/PhotoStatus.java` | `PROCESSING` 상태 추가 |
| `domain/Photo.java` | `sharpness` 필드 추가, `completeProcessing()` 메서드 추가 |
| `service/PhotoUploadService.java` | `upload()` 로직 분리. 스토리지 저장까지만 동기 처리. `getUploadStatus()` 추가 |
| `controller/PhotoController.java` | 업로드 엔드포인트 202 반환으로 변경, status 폴링 엔드포인트 추가 |
| `repository/PhotoRepository.java` | `findByUploadId()` 추가 |
| `docker/init.sql` | `sharpness` 컬럼 추가, CHECK 제약에 `PROCESSING` 추가 |

### PhotoStatus 전이

```
[업로드 요청]
     │
     ▼
 PROCESSING  ── (백그라운드 처리 완료) ──▶  PENDING  ── (POST /calendar/save) ──▶  CONFIRMED
                                                │
                                          (AI 실패 시에도
                                           미분류로 PENDING 전환)
```

---

## 주의사항

### 서버 재시작 시 PROCESSING 상태 잔류
서버가 재시작되면 백그라운드 스레드가 종료되어 `PROCESSING` 상태 사진이 영구적으로 남을 수 있음.
현재는 별도 복구 로직 없음 — 추후 `@EventListener(ApplicationReadyEvent.class)`로 서버 시작 시 `PROCESSING` 사진을 미분류 PENDING으로 처리하는 로직 추가 예정.

### 스레드풀 용량
`photoProcessingExecutor`: core 4, max 10, queue 200. 동시 업로드 요청이 많을 경우 queue 초과 시 `TaskRejectedException` 발생 가능. 현재 MVP 단계에서는 충분한 설정.
