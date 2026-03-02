# Docker 실행 가이드

## 디렉토리 구조

```
docker/
├── docker-compose.local.yml       # MySQL + Redis (개발 기본)
├── docker-compose.monitoring.yml  # Prometheus + Grafana (모니터링)
├── docker-compose.ngrinder.yml    # nGrinder Controller + Agent (성능 테스트)
├── prometheus/
│   └── prometheus.yml             # Prometheus 스크레이프 설정
└── grafana/
    └── provisioning/
        ├── datasources/
        │   └── prometheus.yml     # Prometheus 데이터소스 자동 연결
        └── dashboards/
            └── dashboard.yml      # 대시보드 자동 로드 설정
```

---

## 접속 정보

| 서비스 | URL | 계정 |
|--------|-----|------|
| 앱 API | http://localhost:8080 | - |
| Grafana | http://localhost:3000 | admin / admin |
| Prometheus | http://localhost:9090 | - |
| nGrinder | http://localhost:8300 | admin / admin |
| MySQL | localhost:3306 | application / application |
| Redis | localhost:6379 | - |

---

## 실행 방법

### 개발 환경 (필수)

```bash
# MySQL + Redis 시작
docker compose -f docker/docker-compose.local.yml up -d

# 중지
docker compose -f docker/docker-compose.local.yml down
```

### 모니터링 (Prometheus + Grafana)

```bash
# 시작 (앱이 먼저 실행 중이어야 메트릭 수집 가능)
docker compose -f docker/docker-compose.monitoring.yml up -d

# 중지
docker compose -f docker/docker-compose.monitoring.yml down
```

### 성능 테스트 (nGrinder)

```bash
# 시작
docker compose -f docker/docker-compose.ngrinder.yml up -d

# 중지 (테스트 완료 후)
docker compose -f docker/docker-compose.ngrinder.yml down
```

### 전체 한 번에 실행

```bash
docker compose \
  -f docker/docker-compose.local.yml \
  -f docker/docker-compose.monitoring.yml \
  up -d
```

---

## Grafana 대시보드 설정

### 1. Prometheus 데이터소스 확인

Grafana 기동 시 `grafana/provisioning/datasources/prometheus.yml` 설정에 의해
Prometheus 데이터소스가 자동으로 연결됩니다.

`Connections > Data sources` 에서 Prometheus가 등록되어 있는지 확인하세요.

### 2. 대시보드 Import (권장)

Grafana UI → **Dashboards → Import** → ID 입력 → Load

| 대시보드 | ID | 설명 |
|---------|-----|------|
| Spring Boot 3.x | `19004` | JVM, HTTP, DB 커넥션 풀, GC |
| JVM (Micrometer) | `4701` | JVM 메모리, 스레드, GC 상세 |
| Redis | `11835` | Redis 메모리, 명령어 통계 |

---

## nGrinder 성능 테스트

### 1. Agent 확인

nGrinder UI → **Agent Management** 에서 Agent가 `Approved` 상태인지 확인합니다.
처음 실행 시 `Unapproved` 상태이면 우측 Approve 버튼을 클릭하세요.

### 2. 테스트 스크립트 작성

nGrinder UI → **Script** → **Create** → Groovy 선택

```groovy
import static net.grinder.script.Grinder.grinder
import net.grinder.script.Test
import org.ngrinder.groovy.util.HTTPRequestControl

class TestRunner {
    def test = new Test(1, "알림 발송")
    def request = new HTTPRequestControl()

    @BeforeThread
    void setUp() {
        test.record(this, "sendNotification")
        request.setHeader("Content-Type", "application/json")
    }

    @Test
    void sendNotification() {
        def body = """
        {
            "groupName": "성능테스트",
            "channelType": "EMAIL",
            "sender": "test@example.com",
            "title": "부하 테스트",
            "content": "nGrinder 테스트 메시지",
            "receivers": ["user@example.com"]
        }
        """
        def response = request.POST(
            "http://host.docker.internal:8080/api/notifications",
            body.bytes
        )
        grinder.logger.info("status: ${response.statusCode}")
    }
}
```

### 3. 테스트 실행

**Performance Test** → **Create** → 스크립트 선택 → 가상 사용자/실행 시간 설정 → **Start Test**

---

## 환경 변수

`.env.example`을 복사해서 `.env`로 사용합니다.

```bash
cp docker/.env.example docker/.env
```

| 변수 | 기본값 | 설명 |
|------|--------|------|
| `MYSQL_ROOT_PASSWORD` | root | MySQL root 패스워드 |
| `MYSQL_DATABASE` | notification | DB명 |
| `MYSQL_USER` | application | 앱 DB 계정 |
| `MYSQL_PASSWORD` | application | 앱 DB 패스워드 |

---

## 데이터 초기화

```bash
# 볼륨 포함 전체 삭제 (데이터 초기화)
docker compose -f docker/docker-compose.local.yml down -v
docker compose -f docker/docker-compose.monitoring.yml down -v
```

> ⚠️ `-v` 옵션은 볼륨(DB 데이터 포함)을 함께 삭제합니다.
