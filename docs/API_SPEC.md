# SnapCal Backend API Spec (Current)

Base URL: `http://localhost:8080/api`
Auth: `Authorization: Bearer {accessToken}`

## Response Format

- Success: endpoint별 본문을 직접 반환합니다. (`success/data` 래핑 없음)
- Error:

```json
{
  "code": "VALIDATION_ERROR",
  "message": "month must be between 1 and 12.",
  "field": "month"
}
```

`field`는 validation 에러에서만 선택적으로 포함됩니다.

---

## 1. Auth

### POST `/auth/signup`
Request:
```json
{
  "name": "홍길동",
  "email": "hong@example.com",
  "password": "password123"
}
```

Response `201`:
```json
{
  "accessToken": "...",
  "user": {
    "userId": "uuid",
    "name": "홍길동",
    "email": "hong@example.com",
    "profileImageUrl": null
  }
}
```

- `refreshToken`은 **HttpOnly Cookie**로 내려갑니다.

### POST `/auth/login`
Request:
```json
{
  "email": "hong@example.com",
  "password": "password123"
}
```

Response `200`: `signup`과 동일 구조

### POST `/auth/social`
Request:
```json
{
  "provider": "kakao",
  "accessToken": "provider-token"
}
```

Response `200`: `signup`과 동일 구조 (+ 구현 완료 시 `isNewUser` 포함 가능)

주의: 현재 provider user info fetch는 미구현 상태입니다.

### POST `/auth/refresh`
- Request body 없음
- `refreshToken` 쿠키를 읽어 access token 재발급

Response `200`:
```json
{
  "accessToken": "..."
}
```

---

## 2. Photos

### POST `/photos/upload`
Content-Type: `multipart/form-data`
- field: `photos` (file[])

Response `200`:
```json
{
  "uploadId": "uuid-string",
  "status": "done",
  "duplicateGroups": [
    {
      "groupId": "group-id",
      "takenAt": "2024-03-15",
      "photos": [
        {
          "photoId": "uuid",
          "url": "https://...",
          "takenAt": "2024-03-15"
        }
      ],
      "aiRecommendedPhotoId": "uuid"
    }
  ],
  "classifications": [
    {
      "photoId": "uuid",
      "url": "https://...",
      "takenAt": "2024-03-15",
      "category": "음식"
    }
  ]
}
```

### POST `/photos/duplicates/select`
Request:
```json
{
  "uploadId": "uuid-string",
  "selections": [
    {
      "groupId": "group-id",
      "selectedPhotoId": "uuid",
      "unselectedPhotoIds": ["uuid"]
    }
  ]
}
```

Response `200`:
```json
{
  "message": "Selection completed."
}
```

### PATCH `/photos/{photoId}/category`
Request:
```json
{
  "categoryId": 1
}
```

Response: `204 No Content`

### GET `/photos/{photoId}`
Response `200`:
```json
{
  "photoId": "uuid",
  "url": "https://...",
  "thumbnailUrl": "https://...",
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
```

### DELETE `/photos/{photoId}`
Response: `204 No Content`

---

## 3. Calendar

### POST `/calendar/save`
Request:
```json
{
  "uploadId": "uuid-string"
}
```

Response `200`:
```json
{
  "message": "Saved to calendar."
}
```

### GET `/calendar?year={year}&month={month}`
Response `200`:
```json
{
  "year": 2024,
  "month": 3,
  "days": [
    {
      "date": "2024-03-15",
      "photos": [
        {
          "photoId": "uuid",
          "thumbnailUrl": "https://...",
          "category": "음식",
          "categoryColor": "#FAC775"
        }
      ]
    }
  ]
}
```

---

## 4. Categories

### GET `/categories`
Response `200`:
```json
[
  {
    "categoryId": 1,
    "name": "음식",
    "colorHex": "#FAC775",
    "isDefault": true
  }
]
```

### POST `/categories`
Request:
```json
{
  "name": "카페",
  "colorHex": "#D4A5A5"
}
```

Response `201`: CategoryResponse

### PATCH `/categories/{categoryId}`
Request:
```json
{
  "name": "카페투어",
  "colorHex": "#C49A9A"
}
```

Response `200`: CategoryResponse

### DELETE `/categories/{categoryId}`
Response `200`:
- body: `null`

---

## FE Compatibility Endpoints

### GET `/photos/upload/{uploadId}/status`
Response `200`:
```json
{
  "status": "done",
  "duplicateGroups": null,
  "classifications": [],
  "error": null
}
```

### POST `/photos/{photoId}/edit`
Request body is currently accepted as pass-through options.
Response `200`:
```json
{
  "photoId": "uuid",
  "editedUrl": "https://..."
}
```

### POST `/calendar/export`
Response `200` (placeholder):
```json
{
  "imageUrl": "https://example.com/snapcal/export-placeholder.png",
  "expiresAt": "2026-05-27T10:00:00Z"
}
```

### POST `/calendar/share/link`
Response `200` (placeholder):
```json
{
  "shareUrl": "https://snapcal.app/share/placeholder",
  "expiresAt": "2026-06-03T10:00:00Z"
}
```

---

## Notes for FE Integration

- 현재 BE는 refresh를 쿠키 기반으로 처리합니다.
- FE가 `refreshToken` body 전송을 사용 중이면 계약 통일이 필요합니다.
- Calendar 응답은 `days[]` 구조입니다. FE에서 `dates` 맵 구조를 기대하면 변환이 필요합니다.
