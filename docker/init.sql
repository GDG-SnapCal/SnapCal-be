-- SnapCal DB 초기화 스크립트
-- docker-compose 첫 실행 시 자동으로 실행됨
-- 운영(Supabase)에서는 이 파일을 SQL Editor에서 수동 실행

-- ── 확장 ─────────────────────────────────────────────────────
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- ── 테이블 생성 ───────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS users (
    id               UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    email            VARCHAR(255) NOT NULL UNIQUE,
    password_hash    VARCHAR(255),
    nickname         VARCHAR(50)  NOT NULL,
    profile_image_url TEXT,
    created_at       TIMESTAMP NOT NULL DEFAULT now(),
    updated_at       TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS categories (
    id          SERIAL PRIMARY KEY,
    user_id     UUID REFERENCES users(id) ON DELETE CASCADE,
    name        VARCHAR(50) NOT NULL,
    color_hex   VARCHAR(7),
    is_default  BOOLEAN NOT NULL DEFAULT false,
    created_at  TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS photos (
    id            UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id       UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    original_url  TEXT NOT NULL,
    thumbnail_url TEXT,
    taken_at      DATE,
    exif_available BOOLEAN NOT NULL DEFAULT false,
    phash         BIGINT,
    uploaded_at   TIMESTAMP NOT NULL DEFAULT now()
);

-- 기존 설치 환경 대응 (컬럼이 없을 경우에만 추가)
ALTER TABLE photos ADD COLUMN IF NOT EXISTS phash BIGINT;

CREATE TABLE IF NOT EXISTS photo_categories (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    photo_id        UUID NOT NULL REFERENCES photos(id) ON DELETE CASCADE,
    category_id     INT  NOT NULL REFERENCES categories(id),
    classified_by   VARCHAR(10) NOT NULL DEFAULT 'AI' CHECK (classified_by IN ('AI', 'USER')),
    ai_confidence   FLOAT,
    user_corrected  BOOLEAN NOT NULL DEFAULT false,
    classified_at   TIMESTAMP NOT NULL DEFAULT now()
);

-- ── 인덱스 ───────────────────────────────────────────────────
-- 캘린더 월별 조회 성능 최적화 (user_id + taken_at 복합)
CREATE INDEX IF NOT EXISTS idx_photos_user_taken_at ON photos(user_id, taken_at);
-- 카테고리 필터 조회
CREATE INDEX IF NOT EXISTS idx_photo_categories_category ON photo_categories(category_id);

-- ── 기본 카테고리 시드 데이터 ─────────────────────────────────
INSERT INTO categories (name, color_hex, is_default) VALUES
    ('음식',   '#FAC775', true),
    ('패션',   '#F4C0D1', true),
    ('운동',   '#9FE1CB', true),
    ('풍경',   '#B5D4F4', true),
    ('일상',   '#D3D1C7', true),
    ('미분류', '#E8E8E8', true)
ON CONFLICT DO NOTHING;
