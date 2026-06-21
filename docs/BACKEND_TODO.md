# SnapCal 백엔드 추가 구현 목록

> 코드 리뷰 기반 정리 (2026-06-21)  
> 우선순위: 🔴 즉시 → 🟠 단기 → 🟡 중기 → 🟢 장기

---

## 🔴 즉시 수정 (버그)

### 1. `NoSuchElementException` → 500 오류
- **문제**: `resolveUser()` 등에서 throw하는 `NoSuchElementException`이 `GlobalExceptionHandler`에 없어 500으로 응답
- **파일**: `GlobalExceptionHandler.java`
- **수정**:
```java
@ExceptionHandler(NoSuchElementException.class)
@ResponseStatus(HttpStatus.NOT_FOUND)
public ApiResponse<Void> handleNoSuchElement(NoSuchElementException e) {
    return ApiResponse.error("NOT_FOUND", e.getMessage());
}

@ExceptionHandler(IllegalArgumentException.class)
@ResponseStatus(HttpStatus.BAD_REQUEST)
public ApiResponse<Void> handleIllegalArgument(IllegalArgumentException e) {
    return ApiResponse.error("BAD_REQUEST", e.getMessage());
}
```

---

### 2. ~~CalendarController path variable vs query param 불일치~~ (오탐 — 해당 없음)
- API Spec(`GET /api/calendar?year=&month=`)과 실제 코드(query param) 모두 일치함. 수정 불필요.

---

## 🟠 단기 구현

### 3. AI 분류 confidence score 저장
- **문제**: `PhotoCategory.aiConfidence` 컬럼이 있으나 `classify()`가 String만 반환해 항상 null 저장
- **파일**: `ImageClassificationService.java`, `PhotoUploadService.java`

**① 프롬프트 수정 (JSON 응답 요청)**
```java
private static final String PROMPT =
    "이 이미지를 다음 카테고리 중 하나로 분류하고 JSON으로 응답하세요.\n" +
    "카테고리: 음식, 패션, 운동, 풍경, 일상, 미분류\n" +
    "- 음식: 음식, 요리, 식당, 카페 등\n" +
    "- 패션: 옷, 신발, 액세서리 등\n" +
    "- 운동: 스포츠, 헬스, 야외 운동 등\n" +
    "- 풍경: 자연, 도시, 여행지 풍경 등\n" +
    "- 일상: 사람의 일상적인 활동, 모임, 셀카 등\n" +
    "- 미분류: 동물, 사물, 문서, 스크린샷 등\n" +
    "응답 형식: {\"category\": \"카테고리명\", \"confidence\": 0.0~1.0}\n" +
    "다른 텍스트는 포함하지 마세요.";
```

**② 반환 타입 변경**
```java
// ImageClassificationService.java
public record ClassificationResult(String category, double confidence) {}

public ClassificationResult classify(byte[] imageBytes, String contentType) { ... }
```

**③ confidence 저장**
```java
// PhotoUploadService.java
ImageClassificationService.ClassificationResult result =
        classificationService.classify(bytes, file.getContentType());

photoCategoryRepository.save(PhotoCategory.builder()
        .photo(photo)
        .category(category)
        .classifiedBy(ClassifiedBy.AI)
        .aiConfidence(result.confidence())
        .userCorrected(false)
        .build());
```

---

### 4. 대표 사진 선택/변경 API
- **문제**: 날짜별 캘린더에서 어떤 사진을 대표로 노출할지 지정하는 API 없음
- **신규 파일**: `Photo` 엔티티 필드 추가, Repository 메서드 추가, Controller 엔드포인트 추가

**① Photo 엔티티**
```java
// Photo.java
@Column(name = "is_representative", nullable = false)
@Builder.Default
private boolean isRepresentative = false;

public void setAsRepresentative() { this.isRepresentative = true; }
public void unsetRepresentative()  { this.isRepresentative = false; }
```

**② Repository**
```java
// PhotoRepository.java
Optional<Photo> findByUserIdAndTakenAtAndIsRepresentativeTrue(UUID userId, LocalDate takenAt);
List<Photo> findByUserIdAndTakenAt(UUID userId, LocalDate takenAt);
```

**③ Service**
```java
// PhotoUploadService.java
@Transactional
public void setRepresentative(UUID photoId, User user) {
    Photo photo = photoRepository.findById(photoId)
            .filter(p -> p.getUser().getId().equals(user.getId()))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "사진을 찾을 수 없습니다."));

    // 같은 날짜의 기존 대표 사진 해제
    photoRepository.findByUserIdAndTakenAtAndIsRepresentativeTrue(user.getId(), photo.getTakenAt())
            .ifPresent(Photo::unsetRepresentative);

    photo.setAsRepresentative();
}
```

**④ Controller**
```java
// PhotoController.java
@PatchMapping("/{photoId}/representative")
public ApiResponse<Void> setRepresentative(
        @PathVariable UUID photoId,
        @AuthenticationPrincipal UserDetails userDetails) {
    User user = resolveUser(userDetails);
    photoUploadService.setRepresentative(photoId, user);
    return ApiResponse.ok(null);
}
```

**⑤ DB 마이그레이션**
```sql
ALTER TABLE photos ADD COLUMN is_representative BOOLEAN NOT NULL DEFAULT FALSE;
```

**⑥ CalendarResponse.PhotoEntry에 `isRepresentative` 필드 추가**

---

## 🟡 중기 구현

### 5. OkHttp 타임아웃 명시
- **문제**: OkHttp 기본 타임아웃(10초)으로 OpenAI가 느릴 때 업로드 전체 지연
- **파일**: `StorageConfig.java` (또는 별도 `OpenAiConfig.java`)
- **수정**:
```java
@Bean
public OkHttpClient okHttpClient() {
    return new OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();
}
```

---

### 6. 로그아웃 API + Refresh Token revoke
- **문제**: Refresh Token을 DB에 저장하지 않아 탈취 시 무효화 불가, 로그아웃 API 없음
- **파일**: `AuthController.java`, `AuthService.java`

```java
// AuthController.java
@PostMapping("/logout")
public ApiResponse<Void> logout(HttpServletRequest request, HttpServletResponse response) {
    String refreshToken = extractRefreshTokenFromCookie(request);
    authService.logout(refreshToken);
    // 쿠키 만료 처리
    Cookie cookie = new Cookie(REFRESH_TOKEN_COOKIE, null);
    cookie.setMaxAge(0);
    cookie.setPath("/api/auth/refresh");
    cookie.setHttpOnly(true);
    cookie.setSecure(true);
    response.addCookie(cookie);
    return ApiResponse.ok(null);
}
```

최소 구현: Redis 또는 DB에 블랙리스트 방식으로 무효화된 토큰 저장. `JwtService.isValid()`에서 블랙리스트 확인.

---

### 7. 사진 공유 Presigned URL API
- **문제**: 공유/다운로드 전용 임시 URL 발급 API 없음
- **엔드포인트**: `GET /api/photos/{photoId}/share`

```java
// StorageService.java (S3Presigner 빈 추가 필요)
public String generatePresignedUrl(String fileUrl, long expirationSeconds) {
    String key = extractKey(fileUrl);
    GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
            .signatureDuration(Duration.ofSeconds(expirationSeconds))
            .getObjectRequest(r -> r.bucket(bucket).key(key))
            .build();
    return s3Presigner.presignGetObject(presignRequest).url().toString();
}
```

```java
// PhotoController.java
@GetMapping("/{photoId}/share")
public ApiResponse<Map<String, Object>> getShareUrl(
        @PathVariable UUID photoId,
        @AuthenticationPrincipal UserDetails userDetails) {
    User user = resolveUser(userDetails);
    var photo = photoRepository.findById(photoId)
            .filter(p -> p.getUser().getId().equals(user.getId()))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "사진을 찾을 수 없습니다."));
    String url = storageService.generatePresignedUrl(photo.getOriginalUrl(), 3600);
    return ApiResponse.ok(Map.of("url", url, "expiresIn", 3600));
}
```

> Supabase Storage를 사용 중이라면 `storage.from(bucket).createSignedUrl(path, expiresIn)` 방식으로도 가능.

---

## 🟢 장기 개선

### 8. 대용량 이미지 OOM 방지
- **문제**: `file.getBytes()`로 이미지 전체를 힙에 로드. 사진 10장 × 10MB = 약 1.3GB 순간 소비 가능
- **즉시 적용 가능**: multipart 크기 제한 설정
```yaml
# application.yml
spring:
  servlet:
    multipart:
      max-file-size: 20MB
      max-request-size: 100MB
```
- **장기**: StorageService를 `InputStream` 기반으로 변경, 썸네일 생성 시 원본 전체 디코딩 최소화

---

## 참고: 현재 정상 동작 확인된 항목

| 항목 | 상태 |
|---|---|
| EXIF 없는 사진 null 안전 처리 | ✅ |
| AI 분류 실패 시 미분류 fallback | ✅ |
| AI 분류 결과 사용자 수정 (`PATCH /category`) | ✅ |
| 미분류 카테고리 처리 | ✅ |
| Refresh Token 만료 시 401 응답 | ✅ |
| 중복 업로드 race condition 없음 | ✅ |
| 캘린더 응답 thumbnailUrl → originalUrl fallback | ✅ |
| GlobalExceptionHandler 기본 예외 커버 | ✅ (NoSuchElementException 제외) |
