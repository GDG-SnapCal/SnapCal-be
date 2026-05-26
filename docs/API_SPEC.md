# SnapCal Backend API 명세서

> **Base URL**: `http://localhost:8080`  
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
| 401 | `UNAUTHORIZED` | 인증 실패 또는 토큰 없음 |
| 409 | `CONFLICT` | 중복 데이터 (이메일 등) |
| 500 | `SERVER_ERROR` | 서버 내부 오류 |

---

## JWT 인증 방식

| 구분 | 값 |
|------|----|
| Access Token 유효기간 | 1시간 |
| Refresh Token 유효기간 | 7일 |
| Access Token 전달 방식 | `Authorization: Bearer {token}` 헤더 |
| Refresh Token 전달 방식 | HttpOnly Cookie (`refreshToken`) |

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

> Set-Cookie: `refreshToken=eyJ...; HttpOnly; Secure; Path=/api/auth/refresh; Max-Age=604800`

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

**에러**

| HTTP | code | 조건 |
|------|------|------|
| 401 | `UNAUTHORIZED` | 이메일/비밀번호 불일치 |

---

### 1-3. 소셜 로그인

```
POST /api/auth/social
```

> ⚠️ **현재 미구현** — Kakao/Google API 연동 로직 구현 필요

**Request Body**

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| provider | string | ✅ | `kakao` 또는 `google` |
| accessToken | string | ✅ | 소셜 플랫폼에서 발급받은 Access Token |

```json
{
  "provider": "kakao",
  "accessToken": "kakao_access_token_here"
}
```

**Response** `200 OK`

```json
{
  "success": true,
  "data": {
    "accessToken": "eyJhbGci...",
    "isNewUser": true,
    "user": {
      "userId": "550e8400-e29b-41d4-a716-446655440000",
      "name": "홍길동",
      "email": "hong@kakao.com",
      "profileImageUrl": "https://..."
    }
  }
}
```

> `isNewUser` 는 신규 가입 시에만 `true` 로 포함됩니다.

---

### 1-4. 토큰 갱신

```
POST /api/auth/refresh
```

Cookie에서 `refreshToken`을 읽어 새 Access Token을 발급합니다.

**Cookie 필요**: `refreshToken`

**Response** `200 OK`

```json
{
  "success": true,
  "data": {
    "accessToken": "eyJhbGci..."
  }
}
```

**에러**

| HTTP | code | 조건 |
|------|------|------|
| 401 | `UNAUTHORIZED` | refreshToken 없음 또는 만료 |

---

## 2. 사진 (Photos)

> 모든 엔드포인트에 `Authorization: Bearer {accessToken}` 헤더가 필요합니다.

### 2-1. 사진 업로드 및 AI 분류

```
POST /api/photos/upload
Content-Type: multipart/form-data
```

여러 장의 사진을 한 번에 업로드합니다. 업로드 후:
1. Supabase Storage (로컬: MinIO)에 저장
2. EXIF에서 촬영 날짜 추출
3. GPT-4o mini로 카테고리 자동 분류
4. 같은 날짜 사진이 2장 이상이면 중복 그룹 반환

**Form Data**

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| photos | file[] | ✅ | 사진 파일 목록 |

**Response** `200 OK`

```json
{
  "success": true,
  "data": {
    "uploadId": "a1b2c3d4-...",
    "status": "done",
    "duplicateGroups": [
      {
        "groupId": "e5f6g7h8-...",
        "takenAt": "2024-03-15",
        "photos": [
          {
            "photoId": "uuid-1",
            "url": "https://storage.../photo1.jpg",
            "takenAt": "2024-03-15"
          },
          {
            "photoId": "uuid-2",
            "url": "https://storage.../photo2.jpg",
            "takenAt": "2024-03-15"
          }
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
| `uploadId` | 이후 중복 선택 요청에 사용 |
| `status` | `done` \| `processing` \| `error` |
| `duplicateGroups` | 같은 날 2장 이상일 때만 포함, 없으면 `null` |
| `aiRecommendedPhotoId` | AI가 추천하는 대표 사진 ID |
| `classifications` | 전체 업로드 사진의 AI 분류 결과 |

**카테고리 목록** (시스템 기본값)

| 이름 | 색상 |
|------|------|
| 음식 | `#FAC775` |
| 패션 | `#F4C0D1` |
| 운동 | `#9FE1CB` |
| 여행 | `#B5D4F4` |
| 일상 | `#D3D1C7` |
| 미분류 | `#E8E8E8` |

---

### 2-2. 중복 사진 선택

```
POST /api/photos/duplicates/select
```

업로드 후 중복 그룹이 반환된 경우, 각 그룹에서 남길 사진 1장을 선택합니다. 선택되지 않은 사진은 Storage와 DB에서 삭제됩니다.

**Request Body**

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| uploadId | string | ✅ | 업로드 시 반환된 uploadId |
| selections | array | ✅ | 그룹별 선택 목록 |
| selections[].groupId | string | ✅ | 중복 그룹 ID |
| selections[].selectedPhotoId | string | ✅ | 남길 사진의 photoId |

```json
{
  "uploadId": "a1b2c3d4-...",
  "selections": [
    {
      "groupId": "e5f6g7h8-...",
      "selectedPhotoId": "uuid-1"
    }
  ]
}
```

**Response** `200 OK`

```json
{
  "success": true,
  "data": {
    "message": "선택이 완료되었습니다."
  }
}
```

---

### 2-3. 카테고리 수동 변경

```
PATCH /api/photos/{photoId}/category
```

AI가 분류한 카테고리를 사용자가 직접 변경합니다.

**Path Parameter**

| 파라미터 | 타입 | 설명 |
|----------|------|------|
| photoId | UUID | 사진 ID |

**Request Body**

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| categoryId | integer | ✅ | 변경할 카테고리 ID |

```json
{
  "categoryId": 1
}
```

**Response** `200 OK`

```json
{
  "success": true,
  "data": null
}
```

**에러**

| HTTP | code | 조건 |
|------|------|------|
| 404 | `SERVER_ERROR` | 사진 또는 카테고리를 찾을 수 없음 |

---

## 3. 미구현 API (예정)

### 3-1. 캘린더 월별 조회

```
GET /api/calendar?year={year}&month={month}
Authorization: Bearer {accessToken}
```

특정 월의 사진을 날짜별로 묶어 반환합니다.

**Query Parameters**

| 파라미터 | 타입 | 필수 | 예시 |
|----------|------|------|------|
| year | integer | ✅ | 2024 |
| month | integer | ✅ | 3 |

**예상 Response** `200 OK`

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
            "thumbnailUrl": "https://storage.../thumb_photo1.jpg",
            "category": "음식",
            "categoryColor": "#FAC775"
          }
        ]
      }
    ]
  }
}
```

---

### 3-2. 카테고리 목록 조회

```
GET /api/categories
Authorization: Bearer {accessToken}
```

시스템 기본 카테고리 + 사용자 커스텀 카테고리를 반환합니다.

**예상 Response** `200 OK`

```json
{
  "success": true,
  "data": [
    {
      "categoryId": 1,
      "name": "음식",
      "colorHex": "#FAC775",
      "isDefault": true
    },
    {
      "categoryId": 7,
      "name": "카페",
      "colorHex": "#D4A5A5",
      "isDefault": false
    }
  ]
}
```

---

### 3-3. 커스텀 카테고리 생성

```
POST /api/categories
Authorization: Bearer {accessToken}
```

**예상 Request Body**

```json
{
  "name": "카페",
  "colorHex": "#D4A5A5"
}
```

**예상 Response** `201 Created`

```json
{
  "success": true,
  "data": {
    "categoryId": 7,
    "name": "카페",
    "colorHex": "#D4A5A5",
    "isDefault": false
  }
}
```

---

### 3-4. 카테고리 수정

```
PATCH /api/categories/{categoryId}
Authorization: Bearer {accessToken}
```

> 시스템 기본 카테고리(`isDefault: true`)는 수정 불가

**예상 Request Body**

```json
{
  "name": "카페투어",
  "colorHex": "#C49A9A"
}
```

---

### 3-5. 카테고리 삭제

```
DELETE /api/categories/{categoryId}
Authorization: Bearer {accessToken}
```

> 시스템 기본 카테고리 삭제 불가. 해당 카테고리에 속한 사진은 `미분류`로 이동.

---

### 3-6. 내 프로필 조회

```
GET /api/users/me
Authorization: Bearer {accessToken}
```

**예상 Response** `200 OK`

```json
{
  "success": true,
  "data": {
    "userId": "uuid",
    "name": "홍길동",
    "email": "hong@example.com",
    "profileImageUrl": "https://..."
  }
}
```

---

### 3-7. 프로필 수정

```
PATCH /api/users/me
Authorization: Bearer {accessToken}
Content-Type: multipart/form-data
```

**예상 Form Data**

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| name | string | ❌ | 변경할 이름 |
| profileImage | file | ❌ | 변경할 프로필 이미지 |

---

### 3-8. 사진 상세 조회

```
GET /api/photos/{photoId}
Authorization: Bearer {accessToken}
```

**예상 Response** `200 OK`

```json
{
  "success": true,
  "data": {
    "photoId": "uuid",
    "url": "https://storage.../photo.jpg",
    "thumbnailUrl": "https://storage.../thumb.jpg",
    "takenAt": "2024-03-15",
    "exifAvailable": true,
    "category": {
      "categoryId": 1,
      "name": "음식",
      "colorHex": "#FAC775"
    },
    "classifiedBy": "AI",
    "uploadedAt": "2024-03-15T12:00:00"
  }
}
```

---

### 3-9. 사진 삭제

```
DELETE /api/photos/{photoId}
Authorization: Bearer {accessToken}
```

Storage와 DB에서 모두 삭제됩니다.

**예상 Response** `200 OK`

```json
{
  "success": true,
  "data": null
}
```

---

## 4. ERD 요약

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
  uploaded_at

photo_categories
  id (UUID PK)
  photo_id (FK → photos)
  category_id (FK → categories)
  classified_by (AI | USER)
  ai_confidence (FLOAT)
  user_corrected (BOOL)
  classified_at
```

---

## 5. 구현 현황 요약

| API | 상태 |
|-----|------|
| POST /api/auth/signup | ✅ 완료 |
| POST /api/auth/login | ✅ 완료 |
| POST /api/auth/social | ⚠️ 껍데기만 (Kakao/Google API 연동 미구현) |
| POST /api/auth/refresh | ✅ 완료 |
| POST /api/photos/upload | ✅ 완료 |
| POST /api/photos/duplicates/select | ✅ 완료 |
| PATCH /api/photos/{id}/category | ✅ 완료 |
| GET /api/calendar | ❌ 미구현 |
| GET /api/categories | ❌ 미구현 |
| POST /api/categories | ❌ 미구현 |
| PATCH /api/categories/{id} | ❌ 미구현 |
| DELETE /api/categories/{id} | ❌ 미구현 |
| GET /api/users/me | ❌ 미구현 |
| PATCH /api/users/me | ❌ 미구현 |
| GET /api/photos/{id} | ❌ 미구현 |
| DELETE /api/photos/{id} | ❌ 미구현 |
