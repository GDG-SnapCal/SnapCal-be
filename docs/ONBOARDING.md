# SnapCal 백엔드 온보딩 가이드

처음 프로젝트를 세팅하는 팀원을 위한 가이드입니다.

---

## 사전 준비

아래 도구가 설치되어 있어야 합니다.

| 도구 | 버전 | 확인 명령어 |
|------|------|-------------|
| Java (JDK) | 17 이상 | `java -version` |
| Docker Desktop | 최신 | `docker -v` |
| Git | 최신 | `git -v` |

> IntelliJ IDEA를 사용하는 경우 JDK는 IDE에서 자동 관리 가능합니다.

---

## 1. 저장소 클론

```bash
git clone https://github.com/GDG-SnapCal/SnapCal-be.git
cd SnapCal-be
```

---

## 2. 환경변수 설정

```bash
cp .env.example .env
```

`.env` 파일을 열어 비어있는 값들을 채워주세요.  
**비밀값(비밀번호, API 키)은 팀 리더에게 따로 문의하세요.**

```
SUPABASE_DB_PASSWORD=   ← 팀 리더에게 문의
SUPABASE_ACCESS_KEY=    ← 팀 리더에게 문의
SUPABASE_SECRET_KEY=    ← 팀 리더에게 문의
JWT_SECRET=             ← 팀 리더에게 문의
OPENAI_API_KEY=         ← 팀 리더에게 문의
```

나머지 값들(HOST, USER, ENDPOINT 등)은 `.env.example`에 이미 채워져 있습니다.

---

## 3. 실행 방법 선택

두 가지 방식 중 하나를 선택해서 실행합니다.

### 방식 A — Supabase 연결 (권장, 팀 공용 DB)

팀 전체가 같은 DB를 공유합니다. 내가 넣은 데이터를 다른 팀원도 볼 수 있습니다.

**.env 설정 확인** (기본값이 이미 채워져 있어야 함):
```
SUPABASE_HOST=aws-1-ap-northeast-2.pooler.supabase.com
SUPABASE_USER=postgres.gtqacephnirobjhlyamh
SUPABASE_STORAGE_ENDPOINT=https://gtqacephnirobjhlyamh.supabase.co/storage/v1/s3
SUPABASE_STORAGE_PUBLIC_URL=https://gtqacephnirobjhlyamh.supabase.co/storage/v1
```

**실행:**
```bash
docker compose --profile supabase up --build
```

### 방식 B — 로컬 전용 (내 컴퓨터에만 데이터 저장)

DB와 Storage가 모두 내 컴퓨터 Docker 컨테이너 안에서만 동작합니다.

**.env 설정 변경** (아래 값으로 덮어쓰기):
```
SUPABASE_HOST=db
SUPABASE_USER=postgres
SUPABASE_DB_PASSWORD=snapcal1234
SUPABASE_STORAGE_ENDPOINT=http://minio:9000
SUPABASE_STORAGE_PUBLIC_URL=http://localhost:9000
SUPABASE_ACCESS_KEY=minioadmin
SUPABASE_SECRET_KEY=minioadmin
```

**실행:**
```bash
docker compose up --build
```

---

## 4. 실행 확인

서버가 정상 기동되면 아래 로그가 출력됩니다.

```
HikariPool-1 - Start completed.        ← DB 연결 성공
Started SnapcalBackendApplication ...  ← 서버 기동 완료
```

API 접속 확인:
```bash
curl http://localhost:8080/api/auth/signup
# {"success":false,"error":{"code":"VALIDATION_ERROR",...}} 같은 응답이 오면 정상
```

---

## 5. 포트 정리

| 포트 | 용도 |
|------|------|
| `8080` | Spring Boot API 서버 |
| `5432` | PostgreSQL (방식 B 로컬 전용) |
| `9000` | MinIO S3 API (방식 B 로컬 전용) |
| `9001` | MinIO 웹 콘솔 (방식 B 로컬 전용) — `http://localhost:9001` |

---

## 6. 컨테이너 종료

```bash
# 종료만
docker compose down

# 종료 + 데이터 볼륨까지 삭제 (DB 초기화)
docker compose down -v
```

---

## 7. 자주 묻는 문제

**Q. 빌드 중 멈춰있어요.**  
Docker Desktop이 실행 중인지 확인하세요.

**Q. `port is already allocated` 에러가 나요.**  
8080 포트를 다른 프로세스가 사용 중입니다.
```bash
# Mac/Linux
lsof -ti:8080 | xargs kill -9
```

**Q. DB 연결 오류가 나요.**  
`.env`의 `SUPABASE_DB_PASSWORD`가 올바른지 확인하세요.

**Q. API 호출 시 401이 나요.**  
`POST /api/auth/signup` → `POST /api/auth/login`으로 토큰을 발급받아 `Authorization: Bearer {token}` 헤더에 넣어야 합니다.

---

## 참고 문서

| 문서 | 내용 |
|------|------|
| [API_SPEC.md](API_SPEC.md) | 전체 API 명세 |
| [ASYNC_UPLOAD.md](ASYNC_UPLOAD.md) | 사진 업로드 비동기 처리 흐름 |
| [SUPABASE_MIGRATION.md](SUPABASE_MIGRATION.md) | DB 스키마 변경 이력 |
