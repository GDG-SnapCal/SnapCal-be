# Supabase 마이그레이션 가이드

> 이 문서는 **이미 운영 중인 Supabase 환경**에 스키마 변경사항을 적용할 때 사용합니다.
> 처음 설치하는 경우 `docker/init.sql`을 실행하면 모든 내용이 포함되어 있어 별도 작업이 불필요합니다.

---

## 마이그레이션 적용 방법

1. [Supabase 대시보드](https://supabase.com/dashboard) 접속
2. 프로젝트 선택 → 좌측 메뉴 **SQL Editor** 클릭
3. 아래 각 버전의 SQL을 순서대로 실행

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
