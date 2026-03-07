# Mock 모듈

`mock` 모듈은 로컬 개발/통합 테스트에서 사용하는 독립 실행형 Mock 발송 서버입니다.

## 목적

- 알림 발송 성공/실패/지연 시나리오를 손쉽게 재현합니다.
- API 회복탄력성 테스트를 위한 일관된 JSON 응답을 제공합니다.
- 코드 변경 없이 설정값만으로 동작을 제어할 수 있습니다.

## 실행 방법

저장소 루트에서 실행:

```bash
./gradlew :mock:bootRun
```

기본 포트는 `8081`입니다.

## API

- 엔드포인트: `POST /mock/send`
- Content-Type: `application/json`

### 요청 예시

```json
{
  "requestId": "req-123",
  "channelType": "EMAIL",
  "receiver": "user@example.com",
  "message": "hello",
  "metadata": {
    "source": "local-test"
  }
}
```

## 설정

설정 prefix: `mock`

주요 설정값(`mock/src/main/resources/application.yml` 기준):

- `mock.mode`: `RANDOM | ALWAYS_SUCCESS | ALWAYS_FAIL | ALWAYS_DELAY`
- `notification.external.mock.name`: Feign 클라이언트 이름(기본값 `mockApi`)
- `mock.latency.enabled`: 지연 시뮬레이션 사용 여부
- `mock.latency.probability`: `RANDOM` 모드에서 지연 발생 확률
- `mock.latency.min-ms`: 최소 지연 시간(ms)
- `mock.latency.max-ms`: 최대 지연 시간(ms)
- `mock.failure.enabled`: 실패 시뮬레이션 사용 여부
- `mock.failure.probability`: `RANDOM` 모드에서 실패 발생 확률
- `mock.failure.types`: 실패 시 사용할 HTTP 상태 코드 후보 (예: `500,503,429`)
- `mock.log.include-masked-message-preview`: 로그에 마스킹된 메시지 미리보기 포함 여부
- `mock.log.message-preview-length`: 메시지 미리보기 마스킹 길이

## 동작 모드 요약

- `ALWAYS_SUCCESS`: 항상 성공 응답을 반환합니다.
- `ALWAYS_FAIL`: 항상 실패 응답을 반환합니다.
- `ALWAYS_DELAY`: 항상 지연 후 성공 응답을 반환합니다.
- `RANDOM`: 설정한 확률에 따라 지연/실패를 결정합니다.
