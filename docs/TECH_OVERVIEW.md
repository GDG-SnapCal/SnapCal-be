# SnapCal 백엔드 기술 개요 및 예상 질문 정리

## 목차
1. [시스템 아키텍처](#1-시스템-아키텍처)
2. [핵심 플로우](#2-핵심-플로우)
3. [주요 기술 결정 & 이유](#3-주요-기술-결정--이유)
4. [코드 핵심 로직](#4-코드-핵심-로직)
5. [예상 질문 & 답변](#5-예상-질문--답변)
6. [제한사항 및 미구현 기능](#6-제한사항-및-미구현-기능)
7. [환경 구성 요약](#7-환경-구성-요약)

---

## 1. 시스템 아키텍처

```
[FE: Vercel]  →  [BE: Railway (Spring Boot)]  →  [DB: Supabase PostgreSQL]
                              ↓
                  [Supabase Storage (S3 호환)]
                              ↓
                  [OpenAI GPT-4o mini (AI 분류)]
```

**컴포넌트별 역할**

| 컴포넌트 | 기술 | 역할 |
|----------|------|------|
| FE | React/TS (Vercel) | UI, 사진 업로드, 캘린더 표시 |
| BE | Spring Boot 3.2.5 / Java 17 (Railway) | API 서버, 비즈니스 로직, AI 분류 |
| DB | Supabase PostgreSQL | 사용자/사진/카테고리 데이터 저장 |
| Storage | Supabase Storage (S3 호환) | 이미지 파일 저장 |
| AI | OpenAI GPT-4o mini | 사진 내용 분석 → 카테고리 자동 분류 |

---

## 2. 핵심 플로우

### 사진 업로드 ~ 캘린더 저장 (4단계)

```
① POST /api/photos/upload
   - 사진 파일을 Supabase Storage에 업로드
   - DB에 Photo 레코드 저장 (status = PROCESSING)
   - uploadId 반환 후 즉시 202 응답
   - 백그라운드 스레드에서 AI 분류 + pHash 계산 시작

② GET /api/photos/upload/{uploadId}/status  (폴링)
   - status = "processing": 아직 처리 중 (completed < total)
   - status = "done": 완료 → classifications, duplicateGroups 포함 응답
   - Photo 상태: PROCESSING → PENDING (AI 분류 완료 시)

③ POST /api/photos/duplicates/select  (중복 있을 때만)
   - AI가 pHash로 같은 날 유사 사진 감지 시 그룹으로 묶어 반환
   - 프론트에서 각 그룹에서 남길 사진 1장 선택
   - 나머지는 Storage + DB에서 즉시 삭제

④ POST /api/calendar/save
   - uploadId에 속한 PENDING 사진들을 CONFIRMED로 전환
   - 이 단계 이후부터 캘린더에 표시됨
   - 중복 그룹 없어도 반드시 호출 필요
```

### AI 분류 내부 동작

```
이미지 bytes → Base64 인코딩 → GPT-4o mini Vision API 호출
→ JSON 응답 파싱 { "category": "음식", "confidence": 0.95 }
→ 유효 카테고리 검증 → photo_categories 테이블 저장
→ 실패 시 "미분류"로 자동 fallback (오류가 업로드를 막지 않음)
```

### JWT 인증 흐름

```
로그인/회원가입 → Access Token(1h) + Refresh Token(7d) 발급
→ Access Token: Authorization Bearer 헤더로 매 요청 전송
→ Refresh Token: HttpOnly Secure 쿠키로 저장 (JS 접근 불가)
→ Access Token 만료 시: POST /auth/refresh → 두 토큰 모두 재발급 (Sliding Window)
```

---

## 3. 주요 기술 결정 & 이유

### 비동기 AI 분류 (202 Accepted 패턴)
**왜:** GPT API 호출은 사진 1장당 1~3초 소요. 동기 처리 시 10장 업로드 → 30초 대기 → UX 불량.  
**어떻게:** 업로드 즉시 202 반환 후 백그라운드 스레드풀(`photoProcessingExecutor`, core=4, max=10)에서 처리. 프론트는 1~2초 간격으로 폴링.

### pHash 중복 감지
**왜:** 같은 장소에서 연속 촬영한 사진(버스트샷)을 자동 감지해 사용자가 선택할 수 있도록.  
**어떻게:** 이미지를 32×32 축소 → 그레이스케일 → DCT(이산 코사인 변환) → 상위 8×8 주파수 → 64비트 해시. 두 해시 간 해밍 거리(다른 비트 수) ≤ 10이면 중복 판단.

### Supabase Transaction 모드 Pooler (포트 6543)
**왜:** Free 플랜은 직접 연결 최대 15개 제한. Session 모드 Pooler(5432)는 커넥션을 오래 점유. Transaction 모드로 커넥션 수 대폭 절감.  
**주의:** Transaction 모드에서는 `prepareThreshold=0` 필수 (Prepared Statement 캐시 비활성화).

### HikariCP 커넥션 풀 최소화
- `maximum-pool-size: 5`, `minimum-idle: 2`
- Free 플랜 15개 제한 내에서 안전하게 운영

### ddl-auto: validate
**왜:** 운영 환경에서 Hibernate가 자동으로 스키마를 변경하지 못하도록. 컬럼 추가 시 수동으로 ALTER TABLE 실행 필요.  
**실수 포인트:** 새 컬럼 추가 후 DB 마이그레이션(SQL) 없이 배포하면 서버 시작 실패.

### open-in-view: false
**왜:** HTTP 요청 전체 동안 DB 커넥션을 점유하는 안티패턴 방지.  
**영향:** LAZY 로딩 엔티티는 반드시 `@Transactional` 범위 내에서 접근해야 함. 컨트롤러에서 LAZY 필드 접근 시 `@Transactional(readOnly = true)` 필요.

### CORS: 환경변수로 관리
`CORS_ALLOWED_ORIGINS` Railway 환경변수로 허용 오리진 관리. 기본값은 localhost 3개. FE 배포 URL 변경 시 Railway에서만 수정하면 됨.

---

## 4. 코드 핵심 로직

### Photo 상태 머신
```
PROCESSING → PENDING → CONFIRMED
    ↑             ↑           ↑
AI 분류 전    AI 완료 후   calendar/save 호출 후
              폴링에서 보임  캘린더에 표시됨
```
- AI 실패해도 PENDING으로 전환 (미분류 카테고리로 fallback)
- CONFIRMED만 GET /api/calendar에서 반환됨

### 대표 사진 (is_representative)
- `photos.is_representative`: 날짜 전체 대표 → `GET /api/calendar`의 `representativePhoto`
- `photo_categories.is_category_representative`: 카테고리별 대표 → `categoryRepresentatives`
- 둘 다 `PATCH /api/photos/{id}/representative`로 지정. body에 `category` 있으면 카테고리 대표, 없으면 전체 대표
- 같은 날짜/카테고리에 이미 대표가 있으면 자동으로 기존 것 해제 후 새로 지정

### 카테고리 구조
- **시스템 기본 카테고리** (is_default=true, user_id=null): 음식/패션/운동/풍경/일상/미분류 — AI가 여기에만 분류
- **커스텀 카테고리** (is_default=false, user_id=사용자ID): 사용자가 직접 생성. AI 자동 분류 안 됨. PATCH로 수동 이동만 가능
- 커스텀 카테고리 삭제 시 해당 카테고리 사진들은 자동으로 미분류(기본)로 이동

### EXIF 날짜 추출
- 업로드된 이미지 bytes에서 `ExifSubIFDDirectory.TAG_DATETIME_ORIGINAL` 읽음
- EXIF 없으면 프론트에서 `takenAt` form-data 파라미터로 전달 가능 (2026-06-25 추가)
- 둘 다 없으면 `taken_at = null` → 캘린더 미표시

### StorageService
- Supabase Storage를 S3 호환 API로 사용 (AWS SDK v2)
- 파일 키: `{userId}/{UUID}.{ext}` — 사용자별 디렉토리 분리
- Public URL: `{SUPABASE_STORAGE_PUBLIC_URL}/snapcal-photos/{key}`
- 삭제: URL에서 버킷명 뒤 key 추출 → S3 deleteObject

### 에러 처리 구조
- `GlobalExceptionHandler` — `@RestControllerAdvice`로 전역 예외 처리
- 모든 응답: `{ "success": true/false, "data": ..., "error": { "code", "message", "field" } }`
- AI 분류 실패, pHash 계산 실패 → 예외를 삼키고 미분류 fallback (업로드 자체는 성공)

---

## 5. 예상 질문 & 답변

### 인증/보안

**Q. JWT를 어떻게 저장하고 있나요?**  
Access Token은 메모리/로컬스토리지(FE 결정), Refresh Token은 BE에서 `HttpOnly Secure` 쿠키로 설정. HttpOnly이므로 JS로 접근 불가 → XSS 공격에서 Refresh Token 보호.

**Q. 토큰이 탈취되면 어떻게 하나요?**  
현재 토큰 revoke(블랙리스트) 미구현. Access Token 1시간, Refresh Token 7일이 지나면 자연 만료. 로그아웃 API도 미구현 상태 — 추후 Redis 블랙리스트로 구현 가능.

**Q. 비밀번호는 어떻게 저장하나요?**  
BCrypt 해시로 저장 (`BCryptPasswordEncoder`). 평문 비밀번호는 DB에 저장하지 않음.

**Q. CSRF 방어는?**  
JWT + Stateless 구조이므로 세션 기반 CSRF 취약점 없음. `csrf.disable()` 처리.

### 성능/아키텍처

**Q. 사진 업로드 시 왜 202를 반환하나요?**  
GPT-4o mini 호출에 사진당 1~3초 소요. 동기로 처리하면 10장 업로드 시 최대 30초 대기 발생. 비동기 처리 후 폴링 방식으로 UX 개선.

**Q. 동시에 많은 사용자가 업로드하면 어떻게 되나요?**  
`photoProcessingExecutor` 스레드풀 (core=4, max=10, queue=200)로 처리. 동시 요청이 10개 초과 시 큐에 대기. queue 200개 초과 시 `RejectedExecutionException` 발생 — 현재 별도 처리 없음.

**Q. DB 커넥션은 몇 개 사용하나요?**  
HikariCP 최대 5개. Supabase Free 플랜 한도 15개 내. Transaction 모드 Pooler를 사용해 실제 DB 연결은 더 적게 유지.

**Q. 이미지 파일은 어디에 저장되나요?**  
Supabase Storage (S3 호환). `snapcal-photos` 버킷에 `{userId}/{UUID}.{ext}` 형태로 저장. Public 버킷이라 URL만 있으면 누구나 접근 가능 (현재 별도 접근 제어 없음).

**Q. 썸네일은 어떻게 처리하나요?**  
현재 미구현. `thumbnail_url` 컬럼은 있지만 값이 없고, 응답에서 `thumbnailUrl`이 null이면 `originalUrl`로 자동 fallback. Supabase Image Transformations는 Free 플랜 미지원.

### AI 분류

**Q. AI 분류가 틀리면 어떻게 하나요?**  
`PATCH /api/photos/{id}/category`로 사용자가 직접 수정 가능. 변경 시 `classified_by = USER`, `user_corrected = true`로 기록.

**Q. AI가 반환할 수 있는 카테고리는 몇 가지인가요?**  
6가지로 고정: 음식, 패션, 운동, 풍경, 일상, 미분류. 프롬프트에서 이 6가지만 선택하도록 강제. 유효하지 않은 카테고리 반환 시 자동으로 미분류 처리.

**Q. AI 분류가 실패하면?**  
`미분류` 카테고리로 자동 fallback. GPT 호출 실패, 응답 파싱 실패, 네트워크 오류 등 모든 예외를 catch해서 업로드 자체는 항상 성공하도록 설계.

**Q. GPT 비용은 어떻게 되나요?**  
GPT-4o mini 사용. 이미지 1장당 input token ≈ 1,000~3,000, output은 `max_tokens: 50`으로 제한. 1장당 약 $0.001 미만.

### 중복 감지

**Q. 중복 사진 감지는 어떻게 하나요?**  
pHash(Perceptual Hash) 알고리즘: 이미지를 32×32 축소 → 그레이스케일 → DCT → 64비트 해시. 두 해시의 해밍 거리(다른 비트 수) ≤ 10이면 중복으로 판단. 회전·밝기 변화에 어느 정도 강인함.

**Q. 중복 그룹에서 AI 추천 기준은?**  
`sharpness` (선명도) 값이 가장 높은 사진. 라플라시안 분산으로 계산 — 값이 높을수록 윤곽이 선명한 사진.

### 배포/인프라

**Q. 배포는 어떻게 하나요?**  
Railway에서 Dockerfile 기반으로 빌드/배포. `git push` 후 `railway up --detach --service vibrant-wholeness`로 배포. 자동 배포는 미설정.

**Q. 환경변수 관리는?**  
Railway 환경변수로 주입. `.env` 파일은 로컬 개발용. `application.yml`에서 `${ENV_VAR}` 형태로 참조.

**Q. 로컬 빌드가 안 되는데요?**  
로컬 Java 25/26 + Lombok 1.18.32 호환 문제로 `TypeTag::UNKNOWN` 오류 발생. Lombok 1.18.36으로 업그레이드 적용됨. 그래도 안 되면 Java 17로 실행하거나 Docker로 빌드.

**Q. Railway에서 OOM이 났는데요?**  
런타임 JVM 설정: `-Xmx256m -Xms128m -XX:+UseSerialGC -XX:MaxMetaspaceSize=128m`. 총 JVM 메모리 약 450~500MB. 빌드 시: `GRADLE_OPTS="-Xmx384m"`, `--max-workers=1`.

---

## 6. 제한사항 및 미구현 기능

| 항목 | 현황 | 비고 |
|------|------|------|
| 소셜 로그인 | ❌ 미구현 | 호출 시 501 반환. 카카오/구글 API 연동 필요 |
| 로그아웃 / 토큰 revoke | ❌ 미구현 | Access Token 1h 자연 만료 대기 |
| 썸네일 생성 | ❌ 미구현 | originalUrl로 fallback 처리 중 |
| 프로필 조회/수정 | ❌ 스코프 제외 | GET/PATCH /api/users/me 없음 |
| 이미지 접근 제어 | ⚠️ Public 버킷 | URL 알면 누구나 접근 가능 |
| AI 분류: 커스텀 카테고리 | ⚠️ 불가 | 기본 6개만 자동 분류. 수동 이동만 가능 |
| EXIF 없는 사진 날짜 | ⚠️ 프론트 입력 필요 | takenAt form 파라미터로 전달 가능 |
| 토큰 블랙리스트 | ❌ 미구현 | Redis 도입 시 구현 가능 |

---

## 7. 환경 구성 요약

### Railway 환경변수 (운영)
| 변수명 | 설명 |
|--------|------|
| `SUPABASE_HOST` | Supabase DB 호스트 (pooler) |
| `SUPABASE_PORT` | `6543` (Transaction 모드) |
| `SUPABASE_USER` | `postgres.{프로젝트ID}` |
| `SUPABASE_DB_PASSWORD` | DB 비밀번호 |
| `SUPABASE_STORAGE_ENDPOINT` | S3 엔드포인트 |
| `SUPABASE_STORAGE_PUBLIC_URL` | `{supabase-url}/storage/v1/object/public` |
| `SUPABASE_ACCESS_KEY` | S3 액세스 키 |
| `SUPABASE_SECRET_KEY` | S3 시크릿 키 |
| `JWT_SECRET` | JWT 서명 키 (256bit 이상) |
| `OPENAI_API_KEY` | GPT API 키 |
| `CORS_ALLOWED_ORIGINS` | 허용 FE 오리진 (콤마 구분) |

### 로컬 개발 환경
- `.env` 파일로 환경변수 주입
- Docker Compose: app(8080) / PostgreSQL(5432) / MinIO(9000, 9001)
- Java 17 권장 (Railway 빌드 환경과 동일)

### DB 스키마 변경 시 주의사항
1. Supabase SQL Editor에서 `ALTER TABLE` 실행
2. 그 다음 Railway 재배포
3. 순서가 바뀌면 (`validate` 모드로 인해) 서버 시작 실패
