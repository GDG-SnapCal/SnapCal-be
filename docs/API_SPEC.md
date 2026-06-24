# SnapCal Backend API 명세서

> **Base URL (로컬)**: `http://localhost:8080`  
> **Base URL (운영)**: `https://vibrant-wholeness-production-b4a2.up.railway.app`  
> **Content-Type**: `application/json` (파일 업로드는 `multipart/form-data`)  
> **인증**: `Authorization: Bearer {accessToken}` (명시된 엔드포인트에 한해 필요)

---

## 공통 응답 형식

모든 응답은 아래 구조를 따릅니다.

### 성공
```json
{
  "success": true,
  "data": { ... }
}
```

### 실패
```json
{
  "success": false,
  "error": {
    "code": "ERROR_CODE",
    "message": "오류 메시지",
    "field": "필드명 (validation 오류 시에만 포함)"
  }
}
```

### 공통 에러 코드

| HTTP | code | 설명 |
|------|------|------|
| 400 | `VALIDATION_ERROR` | 요청 필드 검증 실패 |
| 400 | `BAD_REQUEST` | 잘못된 요청 (지원하지 않는 값 등) |
| 401 | `UNAUTHORIZED` | 인증 실패 또는 토큰 없음·만료 |
| 403 | `FORBIDDEN` | 권한 없음 (타인 리소스 접근 등) |
| 404 | `NOT_FOUND` | 리소스를 찾을 수 없음 |
| 409 | `CONFLICT` | 중복 데이터 (이메일 등) |
| 501 | `NOT_IMPLEMENTED` | 미구현 기능 호출 |
| 500 | `SERVER_ERROR` | 서버 내부 오류 |

---

## JWT 인증 방식

| 구분 | 값 |
|------|----|
| Access Token 유효기간 | **1시간** |
| Access Token 전달 방식 | `Authorization: Bearer {token}` 헤더 |
| Refresh Token 유효기간 | 7일 |
| Refresh Token 전달 방식 | `HttpOnly Secure 쿠키` (`refreshToken`) |

> Refresh Token은 `POST /api/auth/refresh` 호출 시 쿠키로 자동 갱신됩니다 (Sliding Window).

---

## 1. 인증 (Auth)

### 1-1. 회원가입

```
POST /api/auth/signup
```

**Request Body**

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| name | string | ✅ | 이름 (최소 2자) |
| email | string | ✅ | 이메일 |
| password | string | ✅ | 비밀번호 (최소 8자) |

```json
{
  "name": "홍길동",
  "email": "hong@example.com",
  "password": "password123"
}
```

**Response** `201 Created`

```json
{
  "success": true,
  "data": {
    "accessToken": "eyJhbGci...",
    "user": {
      "userId": "550e8400-e29b-41d4-a716-446655440000",
      "name": "홍길동",
      "email": "hong@example.com",
      "profileImageUrl": null
    }
  }
}
```

> Refresh Token은 `Set-Cookie` 헤더로 자동 설정됩니다.

**에러**

| HTTP | code | 조건 |
|------|------|------|
| 409 | `CONFLICT` | 이미 가입된 이메일 |
| 400 | `VALIDATION_ERROR` | 필드 검증 실패 |

---

### 1-2. 로그인

```
POST /api/auth/login
```

**Request Body**

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| email | string | ✅ | 이메일 |
| password | string | ✅ | 비밀번호 |

```json
{
  "email": "hong@example.com",
  "password": "password123"
}
```

**Response** `200 OK`

```json
{
  "success": true,
  "data": {
    "accessToken": "eyJhbGci...",
    "user": {
      "userId": "550e8400-e29b-41d4-a716-446655440000",
      "name": "홍길동",
      "email": "hong@example.com",
      "profileImageUrl": null
    }
  }
}
```

> Refresh Token은 `Set-Cookie` 헤더로 자동 설정됩니다.

**에러**

| HTTP | code | 조건 |
|------|------|------|
| 401 | `UNAUTHORIZED` | 이메일/비밀번호 불일치 |

---

### 1-3. 소셜 로그인

```
POST /api/auth/social
```

> ⚠️ **미구현** — 호출 시 `501 NOT_IMPLEMENTED` 반환. Kakao/Google API 연동 로직 추후 구현 예정.

**Request Body**

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| provider | string | ✅ | `kakao` 또는 `google` |
| accessToken | string | ✅ | 소셜 플랫폼에서 발급받은 Access Token |

**에러**

| HTTP | code | 조건 |
|------|------|------|
| 501 | `NOT_IMPLEMENTED` | 소셜 로그인 미구현 |

---

### 1-4. Access Token 갱신

```
POST /api/auth/refresh
```

쿠키의 Refresh Token으로 새 Access Token과 Refresh Token을 발급합니다.  
**Refresh Token도 함께 갱신**되므로 앱을 계속 사용하면 자동으로 로그인이 유지됩니다.

**Request**: 쿠키에 `refreshToken`이 자동으로 포함되면 됩니다. 별도 Body 없음.

**Response** `200 OK`

```json
{
  "success": true,
  "data": {
    "accessToken": "eyJhbGci...",
    "user": {
      "userId": "550e8400-e29b-41d4-a716-446655440000",
      "name": "홍길동",
      "email": "hong@example.com",
      "profileImageUrl": null
    }
  }
}
```

> 갱신된 Refresh Token은 `Set-Cookie` 헤더로 자동 교체됩니다.

**에러**

| HTTP | code | 조건 |
|------|------|------|
| 401 | `UNAUTHORIZED` | Refresh Token 없음 또는 만료 |

---

## 2. 사진 (Photos)

> 모든 엔드포인트에 `Authorization: Bearer {accessToken}` 헤더가 필요합니다.

### 📌 업로드 4단계 플로우

사진을 캘린더에 저장하려면 아래 순서로 API를 호출해야 합니다.

```
① POST /api/photos/upload
      → 스토리지 저장, uploadId 발급, 즉시 202 반환
      → 백그라운드에서 AI 분류·pHash 처리 시작

② GET /api/photos/upload/{uploadId}/status  (1~2초 간격 폴링)
      → status: "processing" → 계속 폴링
      → status: "done" → classifications, duplicateGroups 수신

③ POST /api/photos/duplicates/select  (duplicateGroups가 있을 때만)
      → 각 그룹에서 남길 사진 선택, 나머지 삭제

④ POST /api/calendar/save
      → uploadId 전달 → PENDING → CONFIRMED 전환, 캘린더에 반영
```

> ⚠️ **④를 호출하지 않으면 사진이 캘린더에 표시되지 않습니다.**

---

### 2-1. 날짜별 사진 목록 조회

```
GET /api/photos?date={date}&category={category}
```

특정 날짜의 `CONFIRMED` 사진 목록을 반환합니다. 카테고리 필터는 선택사항입니다.

**Query Parameters**

| 파라미터 | 타입 | 필수 | 설명 |
|----------|------|------|------|
| date | string | ✅ | 조회할 날짜 (`yyyy-MM-dd`) |
| category | string | ❌ | 카테고리 이름으로 필터링 (예: `음식`) |

**Response** `200 OK`

```json
{
  "success": true,
  "data": [
    {
      "photoId": "uuid-1",
      "thumbnailUrl": "https://storage.../photo1.jpg",
      "originalUrl": "https://storage.../photo1.jpg",
      "category": "음식",
      "isRepresentative": true,
      "takenAt": "2024-03-15"
    }
  ]
}
```

| 필드 | 설명 |
|------|------|
| `thumbnailUrl` | 썸네일 미생성 시 원본 URL로 자동 fallback |
| `isRepresentative` | 날짜별 대표 사진 여부 |

> 해당 날짜 사진이 없으면 빈 배열 반환.

---

### 2-2. 사진 업로드

```
POST /api/photos/upload
Content-Type: multipart/form-data
```

여러 장의 사진을 스토리지에 저장하고 AI 분류를 백그라운드에서 시작합니다.

**Form Data**

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| photos | file[] | ✅ | 사진 파일 목록 (장당 최대 20MB) |

**Response** `202 Accepted`

```json
{
  "success": true,
  "data": {
    "uploadId": "a1b2c3d4-...",
    "total": 5
  }
}
```

| 필드 | 설명 |
|------|------|
| `uploadId` | 이후 상태 폴링, 중복 선택, 캘린더 저장 시 사용 |
| `total` | 업로드된 사진 총 수 |

---

### 2-3. 업로드 상태 폴링

```
GET /api/photos/upload/{uploadId}/status
```

백그라운드 AI 분류 진행 상황을 확인합니다. `status: "done"` 이 될 때까지 1~2초 간격으로 폴링합니다.

**Path Parameter**

| 파라미터 | 타입 | 설명 |
|----------|------|------|
| uploadId | string | 업로드 시 발급된 배치 식별자 |

**Response — 처리 중** `202 Accepted`

```json
{
  "success": true,
  "data": {
    "uploadId": "a1b2c3d4-...",
    "status": "processing",
    "total": 5,
    "completed": 2
  }
}
```

**Response — 완료** `200 OK`

```json
{
  "success": true,
  "data": {
    "uploadId": "a1b2c3d4-...",
    "status": "done",
    "total": 5,
    "completed": 5,
    "duplicateGroups": [
      {
        "groupId": "e5f6g7h8-...",
        "takenAt": "2024-03-15",
        "photos": [
          { "photoId": "uuid-1", "url": "https://storage.../photo1.jpg", "takenAt": "2024-03-15" },
          { "photoId": "uuid-2", "url": "https://storage.../photo2.jpg", "takenAt": "2024-03-15" }
        ],
        "aiRecommendedPhotoId": "uuid-1"
      }
    ],
    "classifications": [
      {
        "photoId": "uuid-1",
        "url": "https://storage.../photo1.jpg",
        "takenAt": "2024-03-15",
        "category": "음식"
      }
    ]
  }
}
```

| 필드 | 설명 |
|------|------|
| `status` | `"processing"` \| `"done"` |
| `completed` | AI 분류까지 완료된 사진 수 |
| `duplicateGroups` | 같은 날 유사 사진이 2장 이상일 때만 포함, 없으면 `null` |
| `aiRecommendedPhotoId` | pHash 기반 가장 선명한 사진 ID |
| `classifications` | 전체 업로드 사진의 AI 분류 결과 |

**카테고리 목록** (시스템 기본값)

| 이름 | 색상 |
|------|------|
| 음식 | `#FAC775` |
| 패션 | `#F4C0D1` |
| 운동 | `#9FE1CB` |
| 풍경 | `#B5D4F4` |
| 일상 | `#D3D1C7` |
| 미분류 | `#E8E8E8` |

**에러**

| HTTP | 조건 |
|------|------|
| 404 | 존재하지 않는 uploadId |
| 403 | 본인 업로드 배치가 아님 |

---

### 2-4. 중복 사진 선택

```
POST /api/photos/duplicates/select
```

중복 그룹이 있을 때 각 그룹에서 남길 사진 1장을 선택합니다.  
`unselectedPhotoIds`에 명시된 사진은 Storage와 DB에서 즉시 삭제됩니다.

**Request Body**

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| uploadId | string | ✅ | 업로드 시 반환된 uploadId |
| selections | array | ✅ | 그룹별 선택 목록 |
| selections[].groupId | string | ✅ | 중복 그룹 ID |
| selections[].selectedPhotoId | string | ✅ | 남길 사진의 photoId |
| selections[].unselectedPhotoIds | string[] | ✅ | 삭제할 사진 ID 목록 |

```json
{
  "uploadId": "a1b2c3d4-...",
  "selections": [
    {
      "groupId": "e5f6g7h8-...",
      "selectedPhotoId": "uuid-1",
      "unselectedPhotoIds": ["uuid-2"]
    }
  ]
}
```

**Response** `200 OK`

```json
{
  "success": true,
  "data": { "message": "선택이 완료되었습니다." }
}
```

---

### 2-5. 카테고리 수동 변경

```
PATCH /api/photos/{photoId}/category
```

AI가 분류한 카테고리를 사용자가 직접 변경합니다.  
변경 후 `classifiedBy`가 `USER`로 저장됩니다.

**Path Parameter**

| 파라미터 | 타입 | 설명 |
|----------|------|------|
| photoId | UUID | 사진 ID |

**Request Body**

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| categoryId | integer | ✅ | 변경할 카테고리 ID |

```json
{ "categoryId": 1 }
```

**Response** `200 OK`

```json
{ "success": true, "data": null }
```

---

### 2-6. 대표 사진 지정

```
PATCH /api/photos/{photoId}/representative
```

날짜별 캘린더에 우선 표시할 대표 사진을 지정합니다.  
같은 날짜의 기존 대표 사진이 있으면 자동으로 해제됩니다.

**Path Parameter**

| 파라미터 | 타입 | 설명 |
|----------|------|------|
| photoId | UUID | 대표로 지정할 사진 ID |

**Response** `200 OK`

```json
{ "success": true, "data": null }
```

**에러**

| HTTP | 조건 |
|------|------|
| 404 | 사진을 찾을 수 없거나 본인 소유가 아님 |

---

### 2-7. 사진 상세 조회

```
GET /api/photos/{photoId}
```

**Path Parameter**

| 파라미터 | 타입 | 설명 |
|----------|------|------|
| photoId | UUID | 사진 ID |

**Response** `200 OK`

```json
{
  "success": true,
  "data": {
    "photoId": "uuid-1",
    "url": "https://storage.../photo.jpg",
    "thumbnailUrl": null,
    "takenAt": "2024-03-15",
    "exifAvailable": true,
    "uploadedAt": "2024-03-15T12:00:00",
    "category": {
      "categoryId": 1,
      "name": "음식",
      "colorHex": "#FAC775",
      "classifiedBy": "AI"
    }
  }
}
```

| `classifiedBy` | 설명 |
|----------------|------|
| `AI` | GPT가 자동 분류 |
| `USER` | 사용자가 수동 변경 |

> `thumbnailUrl`은 썸네일 생성 전까지 `null`. 캘린더 표시 시에는 `originalUrl`로 자동 fallback.

**에러**

| HTTP | 조건 |
|------|------|
| 404 | 사진을 찾을 수 없거나 본인 소유가 아님 |

---

### 2-8. 사진 이미지 교체

```
PATCH /api/photos/{photoId}/image
Content-Type: multipart/form-data
```

편집된 이미지로 기존 사진 파일을 교체합니다.  
Storage에서 기존 파일을 삭제하고 새 파일을 업로드한 뒤 `original_url`을 갱신합니다.

**Path Parameter**

| 파라미터 | 타입 | 설명 |
|----------|------|------|
| photoId | UUID | 사진 ID |

**Form Data**

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| file | file | ✅ | 교체할 이미지 파일 (image/jpeg 권장) |

**Response** `200 OK`

```json
{
  "success": true,
  "data": {
    "photoId": "uuid-1",
    "originalUrl": "https://storage.../new-photo.jpg",
    "thumbnailUrl": null
  }
}
```

| 필드 | 설명 |
|------|------|
| `originalUrl` | 교체된 새 이미지 URL |
| `thumbnailUrl` | 썸네일 생성 전까지 `null` |

**에러**

| HTTP | 조건 |
|------|------|
| 404 | 사진을 찾을 수 없거나 본인 소유가 아님 |
| 400 | 파일을 읽을 수 없음 |

---

### 2-9. 사진 삭제

```
DELETE /api/photos/{photoId}
```

Storage와 DB에서 모두 삭제됩니다.

**Path Parameter**

| 파라미터 | 타입 | 설명 |
|----------|------|------|
| photoId | UUID | 사진 ID |

**Response** `200 OK`

```json
{ "success": true, "data": null }
```

**에러**

| HTTP | 조건 |
|------|------|
| 404 | 사진을 찾을 수 없거나 본인 소유가 아님 |

---

## 3. 캘린더 (Calendar)

> 모든 엔드포인트에 `Authorization: Bearer {accessToken}` 헤더가 필요합니다.

### 3-1. 캘린더 저장

```
POST /api/calendar/save
```

업로드 및 중복 검토가 완료된 사진을 캘린더에 최종 반영합니다.  
해당 `uploadId`에 속한 `PENDING` 사진들을 `CONFIRMED` 상태로 전환합니다.

> 중복 그룹이 없었던 경우에도 반드시 호출해야 캘린더에 표시됩니다.

**Request Body**

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| uploadId | string | ✅ | 업로드 시 발급된 배치 식별자 |

```json
{ "uploadId": "a1b2c3d4-..." }
```

**Response** `200 OK`

```json
{
  "success": true,
  "data": { "message": "캘린더에 저장되었습니다." }
}
```

**에러**

| HTTP | 조건 |
|------|------|
| 404 | 저장할 사진 없음 (잘못된 uploadId 또는 이미 저장된 배치) |

---

### 3-2. 캘린더 월별 조회

```
GET /api/calendar?year={year}&month={month}
```

특정 월의 `CONFIRMED` 사진을 날짜별로 묶어 반환합니다.

**Query Parameters**

| 파라미터 | 타입 | 필수 | 설명 |
|----------|------|------|------|
| year | integer | ✅ | 연도 (예: 2024) |
| month | integer | ✅ | 월 (1 ~ 12) |

**Response** `200 OK`

```json
{
  "success": true,
  "data": {
    "year": 2024,
    "month": 3,
    "days": [
      {
        "date": "2024-03-15",
        "photos": [
          {
            "photoId": "uuid-1",
            "thumbnailUrl": "https://storage.../photo1.jpg",
            "category": "음식",
            "categoryColor": "#FAC775",
            "isRepresentative": true
          },
          {
            "photoId": "uuid-2",
            "thumbnailUrl": "https://storage.../photo2.jpg",
            "category": "일상",
            "categoryColor": "#D3D1C7",
            "isRepresentative": false
          }
        ]
      }
    ]
  }
}
```

| 필드 | 설명 |
|------|------|
| `thumbnailUrl` | 썸네일 미생성 시 원본 URL로 자동 fallback |
| `isRepresentative` | 날짜별 대표 사진 여부. `PATCH /api/photos/{id}/representative`로 지정 |

> `days`는 사진이 없는 날짜는 포함하지 않습니다. 해당 월 사진이 없으면 빈 배열 반환.

**에러**

| HTTP | code | 조건 |
|------|------|------|
| 400 | `VALIDATION_ERROR` | month가 1~12 범위 외 |

---

## 4. 카테고리 (Categories)

> 모든 엔드포인트에 `Authorization: Bearer {accessToken}` 헤더가 필요합니다.

> **AI 분류 제약사항**: AI는 시스템 기본 카테고리(음식/패션/운동/풍경/일상/미분류) 6개로만 자동 분류합니다.  
> 커스텀 카테고리로 이동시키려면 `PATCH /api/photos/{id}/category`로 사용자가 직접 변경해야 합니다.

### 4-1. 카테고리 목록 조회

```
GET /api/categories
```

시스템 기본 카테고리 + 내 커스텀 카테고리를 반환합니다.

**Response** `200 OK`

```json
{
  "success": true,
  "data": [
    { "categoryId": 1, "name": "음식", "colorHex": "#FAC775", "isDefault": true },
    { "categoryId": 7, "name": "카페", "colorHex": "#D4A5A5", "isDefault": false }
  ]
}
```

---

### 4-2. 커스텀 카테고리 생성

```
POST /api/categories
```

**Request Body**

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| name | string | ✅ | 카테고리 이름 (최대 50자) |
| colorHex | string | ❌ | 색상 코드 (예: `#D4A5A5`) |

```json
{ "name": "카페", "colorHex": "#D4A5A5" }
```

**Response** `201 Created`

```json
{
  "success": true,
  "data": { "categoryId": 7, "name": "카페", "colorHex": "#D4A5A5", "isDefault": false }
}
```

---

### 4-3. 커스텀 카테고리 수정

```
PATCH /api/categories/{categoryId}
```

> 시스템 기본 카테고리(`isDefault: true`)는 수정 불가 → `403 FORBIDDEN`

**Path Parameter**

| 파라미터 | 타입 | 설명 |
|----------|------|------|
| categoryId | integer | 카테고리 ID |

**Request Body** (변경할 필드만 전송)

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| name | string | ❌ | 변경할 이름 |
| colorHex | string | ❌ | 변경할 색상 코드 |

```json
{ "name": "카페투어", "colorHex": "#C49A9A" }
```

**Response** `200 OK`

```json
{
  "success": true,
  "data": { "categoryId": 7, "name": "카페투어", "colorHex": "#C49A9A", "isDefault": false }
}
```

---

### 4-4. 커스텀 카테고리 삭제

```
DELETE /api/categories/{categoryId}
```

> 시스템 기본 카테고리 삭제 불가. 해당 카테고리에 속한 사진은 `미분류`로 이동.

**Path Parameter**

| 파라미터 | 타입 | 설명 |
|----------|------|------|
| categoryId | integer | 카테고리 ID |

**Response** `200 OK`

```json
{ "success": true, "data": null }
```

---

## 5. 미구현 API (예정)

### 5-1. 내 프로필 조회

```
GET /api/users/me
Authorization: Bearer {accessToken}
```

### 5-2. 프로필 수정

```
PATCH /api/users/me
Authorization: Bearer {accessToken}
Content-Type: multipart/form-data
```

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| name | string | ❌ | 변경할 이름 |
| profileImage | file | ❌ | 변경할 프로필 이미지 |

---

## 6. ERD 요약

```
users
  id (UUID PK)
  email (UNIQUE)
  password_hash (nullable — 소셜 로그인 사용자)
  nickname
  profile_image_url
  created_at / updated_at

categories
  id (SERIAL PK)
  user_id (nullable — null이면 시스템 기본)
  name
  color_hex
  is_default
  created_at

photos
  id (UUID PK)
  user_id (FK → users)
  original_url
  thumbnail_url
  taken_at (DATE)
  exif_available (BOOL)
  phash (BIGINT)           — 중복 사진 감지용 perceptual hash
  sharpness (FLOAT)        — 버스트샷 대표 사진 선택용 선명도 점수
  upload_id (VARCHAR(36))  — 같은 업로드 배치 식별자
  status (PROCESSING | PENDING | CONFIRMED)
    PROCESSING: AI 분류 진행 중
    PENDING:    분류 완료, 캘린더 저장 대기
    CONFIRMED:  캘린더에 최종 반영
  is_representative (BOOL) — 날짜별 대표 사진 여부
  uploaded_at

photo_categories
  id (UUID PK)
  photo_id (FK → photos)
  category_id (FK → categories)
  classified_by (AI | USER)
  ai_confidence (FLOAT)    — GPT 분류 신뢰도 (0.0 ~ 1.0)
  user_corrected (BOOL)
  classified_at
```

---

## 7. 구현 현황 요약

| API | 상태 |
|-----|------|
| POST /api/auth/signup | ✅ 완료 |
| POST /api/auth/login | ✅ 완료 |
| POST /api/auth/social | ⚠️ 미구현 (호출 시 501 반환) |
| POST /api/auth/refresh | ✅ 완료 (Sliding Window 적용) |
| GET /api/photos | ✅ 완료 (날짜별 조회, 카테고리 필터) |
| POST /api/photos/upload | ✅ 완료 (비동기 202 반환) |
| GET /api/photos/upload/{uploadId}/status | ✅ 완료 (폴링) |
| POST /api/photos/duplicates/select | ✅ 완료 |
| PATCH /api/photos/{id}/category | ✅ 완료 |
| PATCH /api/photos/{id}/representative | ✅ 완료 |
| PATCH /api/photos/{id}/image | ✅ 완료 (이미지 교체) |
| GET /api/photos/{id} | ✅ 완료 |
| DELETE /api/photos/{id} | ✅ 완료 |
| POST /api/calendar/save | ✅ 완료 |
| GET /api/calendar | ✅ 완료 |
| GET /api/categories | ✅ 완료 |
| POST /api/categories | ✅ 완료 |
| PATCH /api/categories/{id} | ✅ 완료 |
| DELETE /api/categories/{id} | ✅ 완료 |
| GET /api/users/me | ❌ 미구현 |
| PATCH /api/users/me | ❌ 미구현 |
