# Supabase 마이그레이션 가이드

> 이 문서는 **이미 운영 중인 Supabase 환경**에 스키마 변경사항을 적용할 때 사용합니다.
> 처음 설치하는 경우 `docker/init.sql`을 실행하면 모든 내용이 포함되어 있어 별도 작업이 불필요합니다.

---

## 마이그레이션 적용 방법

1. [Supabase 대시보드](https://supabase.com/dashboard) 접속
2. 프로젝트 선택 → 좌측 메뉴 **SQL Editor** 클릭
3. 아래 각 버전의 SQL을 순서대로 실행

---

## v4 — 비동기 업로드 (PROCESSING 상태 + sharpness 컬럼)

**커밋**: feat: 사진 업로드 비동기 처리 (즉시 202 반환 + 폴링)  
**배경**: 사진 업로드 시 GPT API 동기 호출로 인한 최대 5분 대기 문제 해결.  
업로드 즉시 202 반환 → 백그라운드 AI 분류 → 폴링으로 결과 확인하는 구조로 변경.

```sql
-- 선명도 점수 저장 (버스트샷 대표 사진 선택 + status API 중복 감지 재구성용)
ALTER TABLE photos ADD COLUMN IF NOT EXISTS sharpness FLOAT;

-- status CHECK 제약에 PROCESSING 추가
-- 기존 제약명이 다를 수 있으므로 이름 없는 제약도 함께 제거
ALTER TABLE photos DROP CONSTRAINT IF EXISTS photos_status_check;
ALTER TABLE photos ADD CONSTRAINT photos_status_check
    CHECK (status IN ('PROCESSING', 'PENDING', 'CONFIRMED'));
```

**적용 후 확인 쿼리**
```sql
SELECT column_name, data_type
FROM information_schema.columns
WHERE table_name = 'photos'
  AND column_name = 'sharpness';

SELECT conname, pg_get_constraintdef(oid)
FROM pg_constraint
WHERE conrelid = 'photos'::regclass AND contype = 'c';
```

---

## v3 — 대표 사진 지정 (is_representative 컬럼)

**커밋**: feat: AI confidence score 저장 및 대표 사진 API 구현  
**배경**: 날짜별 캘린더에서 대표 사진을 지정할 수 있도록 `is_representative` 컬럼 추가.  
`PATCH /api/photos/{photoId}/representative` 호출 시 해당 날짜의 기존 대표 사진을 자동 해제.

```sql
ALTER TABLE photos ADD COLUMN IF NOT EXISTS is_representative BOOLEAN NOT NULL DEFAULT FALSE;
```

**적용 후 확인 쿼리**
```sql
SELECT column_name, data_type, column_default
FROM information_schema.columns
WHERE table_name = 'photos'
  AND column_name = 'is_representative';
```

---

## v2 — 3단계 업로드 플로우 (PENDING → CONFIRMED)

**PR**: feat: 3단계 업로드 플로우 구현 (PENDING → CONFIRMED)  
**배경**: 업로드 즉시 캘린더에 반영되던 문제를 해결하기 위해 `status` 컬럼과 업로드 배치 식별자 `upload_id` 컬럼 추가

```sql
-- 업로드 배치 식별자 (같은 업로드 요청에서 생성된 사진끼리 동일한 값)
ALTER TABLE photos ADD COLUMN IF NOT EXISTS upload_id VARCHAR(36) NOT NULL DEFAULT '';

-- 사진 상태 (PENDING: 캘린더 저장 대기 / CONFIRMED: 캘린더에 최종 저장 완료)
-- DEFAULT 'CONFIRMED': 기존 데이터는 이미 캘린더에 반영된 것으로 간주
ALTER TABLE photos ADD COLUMN IF NOT EXISTS status VARCHAR(10) NOT NULL DEFAULT 'CONFIRMED';
```

**적용 후 확인 쿼리**
```sql
SELECT column_name, data_type, column_default
FROM information_schema.columns
WHERE table_name = 'photos'
  AND column_name IN ('upload_id', 'status');
```

---

## v1 — 초기 스키마

`docker/init.sql` 전체 실행으로 구성됩니다. 별도 문서 없음.
