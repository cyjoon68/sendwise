# carrier-mock

모의 SKT, KT, LGU예요. 알림톡 템플릿 검사도 여기서 해요. `api`가 이 서버로 보내고, 결과는 `api`의 콜백으로 돌려줘요.

## 빌드와 테스트

Java 21이 필요해요. Spring Boot 3.5.16은 Gradle 8.x를 지원해요. 이 앱은 Gradle 8.14.6 래퍼를 써요.

저장소 루트에서 테스트를 돌려요.

```bash
gradle -p apps/carrier-mock test
```

래퍼로 고정하려면 같은 자리에서 `apps/carrier-mock/gradlew -p apps/carrier-mock test`를 실행해요. `apps/carrier-mock` 안에서 `./gradlew test`로 돌려도 같아요. 루트 `gradlew`는 없어요. `settings.gradle`에는 `:apps:carrier-mock`가 이미 있어요. Spring Boot 3.5.16 문서는 Gradle 8.x를 지원해요. 래퍼는 8.14.6이에요.

서버를 켜요.

```bash
apps/carrier-mock/gradlew -p apps/carrier-mock bootRun
```

포트는 8081이에요.

## POST /send

본문의 `carrier`로 통신사를 골라요. 경로는 이 하나예요.

```http
POST /send
Content-Type: application/json
```

```json
{
  "messageId": "msg-1",
  "carrier": "SKT",
  "recipient": "01012345678",
  "body": "안녕하세요",
  "channel": "SMS",
  "templateCode": null,
  "variables": null
}
```

| 필드 | 값 |
|---|---|
| `carrier` | `SKT`, `KT`, `LGU` |
| `channel` | `SMS`, `LMS`, `ALIMTALK` |
| `templateCode`, `variables` | 알림톡에서 써요. 문자에서는 무시해요 |

수락하면 바로 202를 줘요. 결과 코드는 이 응답에 없어요.

```json
{
  "messageId": "msg-1",
  "carrier": "SKT",
  "accepted": true
}
```

거절하면 `accepted`가 false예요.

| 상태 | `reason` |
|---|---|
| 400 | `MISSING_MESSAGE_ID`, `UNKNOWN_CARRIER`, `MISSING_RECIPIENT`, `MISSING_BODY`, `UNKNOWN_CHANNEL` |
| 429 | `TPS_EXCEEDED` |

```json
{
  "messageId": "msg-1",
  "carrier": "SKT",
  "accepted": false,
  "reason": "TPS_EXCEEDED"
}
```

TPS는 통신사마다 최근 1초 안의 수락 건수예요. 한도를 넘으면 그 건은 콜백을 만들지 않아요.

## 결과 콜백

수락한 건은 `callback-delay-millis + 지연` 뒤에 `api`로 POST 해요.

```http
POST {callback-base-url}/carrier/{carrier}/results
Content-Type: application/json
```

`{carrier}`는 요청의 `SKT`, `KT`, `LGU`예요.

```json
{
  "messageId": "msg-1",
  "carrier": "SKT",
  "recipient": "01012345678",
  "channel": "SMS",
  "carrierCode": "0",
  "commonCode": "SUCCESS"
}
```

`carrierCode`는 `packages/rules/carrier-codes.json`에 있는 값이에요. `commonCode`는 그 표의 공통 코드예요. 예시는 그 파일 기준이에요.

콜백을 잃는 설정이면 수락은 하고 POST는 하지 않아요. 콜백 HTTP가 실패하면 로그로 남기고 재시도하지 않아요.

## 결과 코드

`packages/rules/carrier-codes.json`만 읽어요. 앱 안에 복사본은 없어요.

찾는 순서는 이렇게요.

1. `carrier-mock.codes-path`가 있으면 그 파일
2. `packages/rules/carrier-codes.json`
3. `../packages/rules/carrier-codes.json`
4. `../../packages/rules/carrier-codes.json`

작업 디렉터리에 따라 2~4 중 있는 파일을 써요. `apps/carrier-mock`에서 실행하면 4번을 찾아요.

최상위 `mock: true`처럼 통신사가 아닌 키는 건너뛰어요. `SKT`, `KT`, `LGU`만 써요.

공통 코드에서 통신사 코드로 바꿀 때는 파일에 먼저 나온 코드를 골라요.

## 알림톡

기본 템플릿은 두 개예요.

| `templateCode` | 본문 |
|---|---|
| `ORDER_SHIPPED` | `#{name}님, 주문 #{orderNo}가 출고되었습니다.` |
| `AUTH_CODE` | `인증번호는 #{code}입니다.` |

`channel`이 `ALIMTALK`이면 `#{변수}`를 `variables`로 바꾼 본문과 `body`가 같아야 해요. 변수가 없으면 빈 문자열로 바꿔요. 템플릿이 없거나 본문이 다르면 실패예요.

공유 표에는 템플릿 불일치 공통 코드가 없어요. 그래서 그 실패는 그 통신사의 `ETC` 코드로 콜백해요. SKT `999`, KT `E999`, LGU `X`예요. `api` 매핑이 이 코드를 `ETC`로 풀어요.

맞는 본문 예시는 `홍길동님, 주문 A1가 출고되었습니다.`예요. `variables`는 `{"name":"홍길동","orderNo":"A1"}`예요.

## 설정

`src/main/resources/application.yml` 키예요. 기본 한도와 비율은 측정값이 아니에요.

| 키 | 의미 |
|---|---|
| `carrier-mock.callback-base-url` | `api` 주소. 기본 `http://localhost:8080` |
| `carrier-mock.random-seed` | 통신사별 난수 시드 |
| `carrier-mock.codes-path` | 코드 표를 이 경로로 고정 |
| `carriers.<SKT\|KT\|LGU>.max-tps` | 1초당 수락 한도. 0이면 전부 429 |
| `carriers.*.callback-delay-millis` | 콜백 전 대기 |
| `carriers.*.callback-loss-ratio` | 0~1. 1이면 콜백을 전부 버려요 |
| `carriers.*.latency.distribution` | `fixed` 또는 `uniform` |
| `carriers.*.latency.min-millis` / `max-millis` | `fixed`는 `min`만 써요. `uniform`은 양 끝 포함 |
| `carriers.*.failure-ratio` | `INVALID_NUMBER`, `OPTED_OUT`, `DEVICE_OFF`, `SPAM_BLOCKED`, `ETC`. 합이 1을 넘으면 거절해요. 나머지는 `SUCCESS` |
| `templates[].code` / `body` | 승인된 알림톡 템플릿 |

난수는 통신사마다 `java.util.Random(random-seed)` 하나예요. 통신사끼리는 시드를 공유하지 않아요. 수락할 때마다 이 순서로 뽑아요.

1. `uniform`이면 지연 한 번. `fixed`는 뽑지 않아요.
2. 결과 코드 한 번. 알림톡 템플릿이 어긋나면 이 추첨은 건너뛰고 `ETC`를 써요.
3. 콜백 유실 한 번.

비율이 0이거나 1이어도 2번과 3번은 뽑아요. 시드가 같으면 같은 순서가 나와요. 관리 API로 비율을 바꿔도 시드는 유지해요.

## PUT /admin/carriers/{carrier}

실행 중에 그 통신사 설정만 바꿔요. 없는 필드는 그대로 둬요. `failureRatio`를 주면 맵 전체를 그 값으로 바꿔요. 빠뜨린 실패 유형은 0이에요.

```http
PUT /admin/carriers/SKT
Content-Type: application/json
```

```json
{
  "maxTps": 1,
  "callbackDelayMillis": 0,
  "callbackLossRatio": 0,
  "latency": { "distribution": "fixed", "minMillis": 0, "maxMillis": 0 },
  "failureRatio": {
    "INVALID_NUMBER": 0,
    "OPTED_OUT": 0,
    "DEVICE_OFF": 0,
    "SPAM_BLOCKED": 0,
    "ETC": 1
  }
}
```

200이면 저장된 설정을 돌려줘요. 400이면 `{"reason":"..."}`예요. `UNKNOWN_CARRIER`, `INVALID_MAX_TPS`, `INVALID_CALLBACK_DELAY`, `INVALID_CALLBACK_LOSS_RATIO`, `INVALID_LATENCY`, `INVALID_FAILURE_RATIO` 중 하나예요.

## 시나리오

아래 값은 설정이에요. 측정값이 아니에요.

보통 발송. 실패 비율 0, 콜백 유실 0.

```bash
curl -s -X POST localhost:8081/send \
  -H 'content-type: application/json' \
  -d '{"messageId":"n1","carrier":"SKT","recipient":"01012345678","body":"안녕하세요","channel":"SMS"}'
```

202 다음에 `carrierCode` `0`, `commonCode` `SUCCESS` 콜백이 가요.

TPS를 막아요.

```bash
curl -s -X PUT localhost:8081/admin/carriers/SKT \
  -H 'content-type: application/json' \
  -d '{"maxTps":1}'
```

바로 두 번 POST 하면 두 번째는 429 `TPS_EXCEEDED`예요.

실패를 늘려요.

```bash
curl -s -X PUT localhost:8081/admin/carriers/KT \
  -H 'content-type: application/json' \
  -d '{"failureRatio":{"ETC":1}}'
```

KT 콜백은 `carrierCode` `E999`, `commonCode` `ETC`예요.

콜백을 버려요.

```bash
curl -s -X PUT localhost:8081/admin/carriers/LGU \
  -H 'content-type: application/json' \
  -d '{"callbackLossRatio":1}'
```

POST는 202예요. `POST /carrier/LGU/results`는 가지 않아요.

템플릿이 어긋난 알림톡도 202예요. SKT면 콜백 `carrierCode`는 `999`예요.
