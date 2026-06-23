CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

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
    id                UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id           UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    original_url      TEXT NOT NULL,
    thumbnail_url     TEXT,
    taken_at          DATE,
    exif_available    BOOLEAN NOT NULL DEFAULT false,
    phash             BIGINT,
    sharpness         FLOAT,
    uploaded_at       TIMESTAMP NOT NULL DEFAULT now(),
    upload_id         VARCHAR(36) NOT NULL DEFAULT '',
    status            VARCHAR(10) NOT NULL DEFAULT 'CONFIRMED'
                          CHECK (status IN ('PROCESSING', 'PENDING', 'CONFIRMED')),
    is_representative BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE IF NOT EXISTS photo_categories (
    id             UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    photo_id       UUID NOT NULL REFERENCES photos(id) ON DELETE CASCADE,
    category_id    INT  NOT NULL REFERENCES categories(id),
    classified_by  VARCHAR(10) NOT NULL DEFAULT 'AI' CHECK (classified_by IN ('AI', 'USER')),
    ai_confidence  FLOAT,
    user_corrected BOOLEAN NOT NULL DEFAULT false,
    classified_at  TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_photos_user_taken_at ON photos(user_id, taken_at);
CREATE INDEX IF NOT EXISTS idx_photo_categories_category ON photo_categories(category_id);

INSERT INTO categories (name, color_hex, is_default) VALUES
    ('음식',   '#FAC775', true),
    ('패션',   '#F4C0D1', true),
    ('운동',   '#9FE1CB', true),
    ('풍경',   '#B5D4F4', true),
    ('일상',   '#D3D1C7', true),
    ('미분류', '#E8E8E8', true)
ON CONFLICT DO NOTHING;
