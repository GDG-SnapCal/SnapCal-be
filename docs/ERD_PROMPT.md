# ERD 작성용 프롬프트

> Claude 등 AI에게 아래 내용을 그대로 붙여넣으면 ERD를 생성해줍니다.
> 기본값은 Mermaid 문법이며, 다른 도구를 원하면 마지막 줄을 수정하세요.
> (예: `"Mermaid 대신 dbdiagram.io DBML 문법으로 작성해줘"`)

---

아래 스키마를 바탕으로 ERD 다이어그램을 작성해줘.
Mermaid erDiagram 문법으로 작성해줘.

---

## 테이블 정의

### users
| 컬럼 | 타입 | 제약 | 설명 |
|------|------|------|------|
| id | UUID | PK | 자동 생성 |
| email | VARCHAR(255) | NOT NULL, UNIQUE | 이메일 |
| password_hash | VARCHAR(255) | nullable | 소셜 로그인 사용자는 null |
| nickname | VARCHAR(50) | NOT NULL | 닉네임 |
| profile_image_url | TEXT | nullable | 프로필 이미지 URL |
| created_at | TIMESTAMP | NOT NULL | 생성일시 |
| updated_at | TIMESTAMP | NOT NULL | 수정일시 |

### categories
| 컬럼 | 타입 | 제약 | 설명 |
|------|------|------|------|
| id | SERIAL | PK | 자동 증가 정수 |
| user_id | UUID | nullable, FK → users.id | null이면 시스템 기본 카테고리 |
| name | VARCHAR(50) | NOT NULL | 카테고리 이름 |
| color_hex | VARCHAR(7) | nullable | 색상 코드 (예: #FAC775) |
| is_default | BOOLEAN | NOT NULL, DEFAULT false | 시스템 기본 여부 |
| created_at | TIMESTAMP | NOT NULL | 생성일시 |

### photos
| 컬럼 | 타입 | 제약 | 설명 |
|------|------|------|------|
| id | UUID | PK | 자동 생성 |
| user_id | UUID | NOT NULL, FK → users.id | 업로드한 사용자 |
| original_url | TEXT | NOT NULL | 스토리지 원본 URL |
| thumbnail_url | TEXT | nullable | 썸네일 URL |
| taken_at | DATE | nullable | EXIF 촬영일 (없으면 null) |
| exif_available | BOOLEAN | NOT NULL, DEFAULT false | EXIF 추출 성공 여부 |
| phash | BIGINT | nullable | 지각적 해시값 (중복 감지용) |
| upload_id | VARCHAR(36) | NOT NULL, DEFAULT '' | 업로드 배치 식별자 |
| status | VARCHAR(10) | NOT NULL, DEFAULT 'CONFIRMED' | PENDING(캘린더 저장 대기) / CONFIRMED(캘린더 반영 완료) |
| uploaded_at | TIMESTAMP | NOT NULL | 업로드일시 |

### photo_categories
| 컬럼 | 타입 | 제약 | 설명 |
|------|------|------|------|
| id | UUID | PK | 자동 생성 |
| photo_id | UUID | NOT NULL, FK → photos.id | 사진 |
| category_id | INT | NOT NULL, FK → categories.id | 카테고리 |
| classified_by | VARCHAR(10) | NOT NULL, DEFAULT 'AI' | AI / USER |
| ai_confidence | FLOAT | nullable | AI 분류 신뢰도 |
| user_corrected | BOOLEAN | NOT NULL, DEFAULT false | 사용자 수동 변경 여부 |
| classified_at | TIMESTAMP | NOT NULL | 분류일시 |

---

## 관계 정리
- users 1 ── N categories (user_id nullable: 시스템 기본 카테고리는 user_id = null)
- users 1 ── N photos
- photos 1 ── 1 photo_categories (사진 1장에 카테고리 1개)
- categories 1 ── N photo_categories

## 인덱스
- photos(user_id, taken_at) — 캘린더 월별 조회 최적화
- photo_categories(category_id) — 카테고리 필터 조회

## 기본 카테고리 시드 데이터 (is_default=true, user_id=null)
음식(#FAC775), 패션(#F4C0D1), 운동(#9FE1CB), 풍경(#B5D4F4), 일상(#D3D1C7), 미분류(#E8E8E8)
