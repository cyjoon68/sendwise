# sendwise api

문자와 알림톡 발송 API예요. 포트는 `8080`이에요. 통신사 모의 서버는 `8081`이고, 모의 서버의 `callback-base-url` 기본값은 `http://localhost:8080`이에요. API 포트를 바꾸면 모의 서버의 `carrier-mock.callback-base-url`도 같은 주소로 맞추세요.

인증은 없어요. 고객사는 요청의 `clientId`로 구분해요.

시드 데이터는 고객사 `1` (`demo`), 잔액 `1000000`, 승인된 발신번호 `0212345678`, 승인된 알림톡 템플릿 `WELCOME`, 수신거부 `01099998888`이에요.

## 실행과 테스트

Java 21과 Docker가 필요해요. MySQL은 테스트가 Testcontainers `mysql:8.4`로 띄워요. 로컬 실행은 저장소 `compose.yaml`의 MySQL(`sendwise` / `sendwise` / `sendwise`)을 써요.

이 저장소 루트에는 `gradlew`가 없어요. 테스트는 이 명령으로 통과했어요.

```bash
apps/api/gradlew -p apps/api test
```

`apps/api` 안에서 `./gradlew test`로 돌려도 같아요. 래퍼는 Gradle 8.14.3이에요.

```bash
apps/api/gradlew -p apps/api bootRun
```

## 통신사로 보내는 요청

`sendwise.carrier-base-url` 기본값은 `http://localhost:8081`이에요. 알림톡도 같은 `POST /send`로 보내요. 본문의 `carrier`는 `SKT`, `KT`, `LGU` 중 하나예요. 수신번호 마지막 숫자 `% 3`으로 정해요. `0`은 SKT, `1`은 KT, `2`는 LGU예요.

```json
{
  "messageId": "15",
  "carrier": "SKT",
  "recipient": "01012345670",
  "body": "안녕 홍길동",
  "channel": "ALIMTALK",
  "templateCode": "WELCOME",
  "variables": { "name": "홍길동" }
}
```

`messageId`는 숫자 아이디의 문자열이에요. 알림톡은 수신자별 `variables`를 그대로 보내요. `body`는 `#{변수}`를 그 값으로 바꾼 문장이에요. 빠진 변수는 빈 문자열이에요. 그래서 승인된 템플릿과 값이 맞으면 모의 서버 본문 검사에 통과해요. 문자(`SMS`, `LMS`)는 바꾼 `body`만 보내고 `variables`는 `null`, `templateCode`도 `null`이에요.

| 응답 | 처리 |
|---|---|
| 202 `accepted: true` | 메시지 상태를 `SENT`로 바꿔요 |
| 429 `reason: TPS_EXCEEDED` | 보내지 않은 것으로 보고 `QUEUED`에 다시 넣어요. 실패로 바꾸지 않아요 |
| 400 `MISSING_MESSAGE_ID`, `UNKNOWN_CARRIER`, `MISSING_RECIPIENT`, `MISSING_BODY`, `UNKNOWN_CHANNEL` | `FAILED`로 바꾸고 차감한 금액을 돌려요. 대체발송은 만들지 않아요 |

그 외 HTTP 상태나 연결 실패도 실패로 확정하지 않고 `QUEUED`로 돌려요. 모의 서버가 계속 죽으면 메시지는 `QUEUED`에 남아요.

모의 서버가 콜백을 버리도록 설정하면 수락 뒤 결과가 오지 않아요. 그 메시지는 `SENT`로 남겨요. MVP에서는 재조회나 타임아웃 실패를 하지 않아요.

## 결과 콜백

모의 서버가 아래로 보내요.

```http
POST /carrier/{carrier}/results
Content-Type: application/json
```

`{carrier}`는 `SKT`, `KT`, `LGU`예요. 공통 코드는 이 경로의 통신사와 `carrierCode`를 `packages/rules/carrier-codes.json`에 대조해서 정해요. 본문의 `commonCode`는 대조 결과와 다를 때 로그만 남겨요. 상태 계산에는 쓰지 않아요.

```json
{
  "messageId": "15",
  "carrier": "SKT",
  "recipient": "01012345670",
  "channel": "ALIMTALK",
  "carrierCode": "0",
  "commonCode": "SUCCESS"
}
```

`messageId`는 숫자 문자열이나 숫자 모두 받아요. `carrierCode`가 없으면 예전 필드 `code`를 읽어요.

이미 `DELIVERED`나 `FAILED`인 메시지에 같은 요청이 오면 상태를 덮어쓰지 않고 현재 결과를 돌려줘요.

`SUCCESS`면 `DELIVERED`예요. 알림톡에 잡아 둔 대체발송 금액을 환불해요. 그 외 코드(매핑이 안 되는 코드 포함)는 `FAILED`이고 메시지 발송 금액을 환불해요. 알림톡이고 캠페인 대체가 `SAME` 또는 `CUSTOM`이면 대체 문자 메시지를 만들어요. 템플릿이 어긋나면 모의 서버가 그 통신사의 `ETC` 코드로 콜백해요. SKT `999`, KT `E999`, LGU `X`예요. `ETC`도 성공이 아니라서 같은 대체 경로를 타요.

```json
{
  "messageId": 15,
  "status": "FAILED",
  "carrierCode": "101",
  "commonCode": "INVALID_NUMBER",
  "explanation": "없는 번호라 전달하지 못했어요",
  "actionHint": "번호를 확인한 뒤 다시 등록해 주세요",
  "fallbackMessageId": 16
}
```

대체 메시지를 안 만들면 `fallbackMessageId`는 `null`이에요.

## 공통 오류

```json
{ "error": "INSUFFICIENT_BALANCE", "message": "잔액이 부족해요", "balance": 1, "cost": 9, "shortfall": 8 }
```

본문을 읽을 수 없으면 `400` `VALIDATION`, `요청 값을 확인해 주세요`예요.

| error | HTTP | 의미 |
|---|---|---|
| `CLIENT_REQUIRED` | 400 | `clientId`가 없어요 |
| `BODY_REQUIRED` | 400 | 본문이 비었어요 |
| `INVALID_CHANNEL` `INVALID_PURPOSE` `INVALID_FALLBACK` | 400 | 허용 값이 아니에요 |
| `FALLBACK_NOT_ALLOWED` | 400 | 대체발송은 알림톡만 가능해요 |
| `FALLBACK_BODY_REQUIRED` | 400 | `CUSTOM`인데 대체 문구가 없어요 |
| `TEMPLATE_REQUIRED` `TEMPLATE_NOT_APPROVED` | 400 | 승인된 템플릿 코드가 필요해요 |
| `INVALID_SENDER` `SENDER_NOT_APPROVED` | 400 | 발신번호 형식 또는 승인 상태가 아니에요 |
| `CLIENT_NOT_FOUND` | 404 | 고객사가 없어요 |
| `RECIPIENTS_REQUIRED` `INVALID_RECIPIENT` `NO_RECIPIENTS` | 400 | 수신번호가 없거나 `010` 8자리가 아니에요 |
| `INVALID_VARIABLES` | 400 | `variables`가 객체가 아니에요 |
| `CHANNEL_MISMATCH` | 400 | 본문 길이와 채널이 달라요 |
| `UNENCODABLE` | 400 | KS X 1001에 없는 글자예요 |
| `BODY_TOO_LONG` | 400 | 2,000바이트를 넘어요 |
| `AD_BLOCKED_HOURS` | 400 | 21:00 포함, 08:00 미포함. 한국 시간이에요 |
| `AD_PREFIX_REQUIRED` | 400 | `(광고)`로 시작해야 해요 |
| `AD_OPTOUT_REQUIRED` | 400 | `무료수신거부 080-000-0000`이 있어야 해요 |
| `AD_COMPANY_REQUIRED` | 400 | 본문에 `companyName`이 있어야 해요 |
| `INSUFFICIENT_BALANCE` | 402 | 잔액이 비용보다 적어요. 차감하지 않아요 |
| `CAMPAIGN_NOT_FOUND` `MESSAGE_NOT_FOUND` `SENDER_NOT_FOUND` | 404 | 대상이 없어요 |
| `CODE_REQUIRED` `MESSAGE_ID_REQUIRED` | 400 | 콜백 코드나 메시지 번호가 없어요 |
| `INVALID_STATUS` | 409 | 허용되지 않은 상태 변경이에요 |
| `INVALID_PAGE` | 400 | 페이지가 0보다 작거나 크기가 1–100 밖이에요 |
| `RANGE_REQUIRED` `INVALID_RANGE` `INVALID_GROUP` | 400 | 통계 기간 또는 `groupBy`가 잘못됐어요 |
| `EXPORT_NOT_READY` | 409 | 엑셀이 아직 `DONE`이 아니에요 |
| `DUPLICATE_SENDER` | 409 | 같은 고객사에 같은 번호가 있어요 |
| `REJECT_REASON_REQUIRED` | 400 | 반려 사유가 없어요 |

잔액 차감은 이 SQL 한 번이에요. 0건이면 402예요.

```sql
UPDATE balance SET amount = amount - :cost WHERE client_id = :id AND amount >= :cost
```

예상 비용은 `(채널 단가 + 대체 단가) * 발송 수신번호 수`예요. 단가는 SMS 9, LMS 30, 알림톡 7이에요. 수신거부 번호는 건너뛰고 비용에 넣지 않아요. 중복 번호는 한 번만 보내요.

## GET /v1/rules

`packages/rules/message-rules.json`을 돌려줘요. 파일이 없거나 키가 빠지면 기본값으로 채워요. `carrier-codes.json`의 `mock: true`처럼 객체가 아닌 키는 코드 표에서 건너뛰어요.

## POST /v1/campaigns

`201`

```json
{
  "clientId": 1,
  "channel": "SMS",
  "purpose": "NORMAL",
  "body": "ping",
  "fallback": "NONE",
  "fallbackBody": null,
  "senderNumber": "0212345678",
  "templateCode": null,
  "companyName": null,
  "scheduledAt": null,
  "recipients": [
    { "phone": "010-1234-5670", "variables": { "name": "홍길동" } }
  ]
}
```

수신자는 문자열 `"01012345670"`이어도 돼요. 그때 `variables`는 없어요. 객체면 `phone`(또는 `recipient`)과 `variables` 객체예요. `variables` 값은 문자열로 저장해요. 없는 키는 빈 문자열로 바꿔요.

문자 채널은 바꾼 본문이 그 채널 길이와 같아야 해요. 알림톡 대체발송 `SAME`은 바꾼 본문, `CUSTOM`은 `fallbackBody`를 같은 변수로 바꾼 문장으로 금액을 잡아요.

`channel`은 `SMS`, `LMS`, `ALIMTALK`이에요. `purpose`는 `AUTH`, `NORMAL`, `AD`예요. `fallback`은 `NONE`, `SAME`, `CUSTOM`이에요. `scheduledAt`을 생략하면 지금 시각으로 광고 시간을 검사해요. 형식은 ISO-8601이에요.

```json
{
  "id": 3,
  "estimatedCost": 9,
  "balanceAfter": 999991,
  "messageCount": 1,
  "skippedOptOut": []
}
```

## GET /v1/campaigns/{id}

```json
{
  "id": 3,
  "clientId": 1,
  "channel": "SMS",
  "purpose": "NORMAL",
  "fallback": "NONE",
  "senderNumber": "0212345678",
  "templateCode": null,
  "scheduledAt": null,
  "estimatedCost": 9,
  "createdAt": "2026-10-22T00:00:00Z",
  "counts": { "QUEUED": 1, "SENT": 0, "DELIVERED": 0, "FAILED": 0 },
  "fallbackCount": 0
}
```

## GET /v1/campaigns/{id}/messages

쿼리는 `status`, `page`(기본 0), `size`(기본 20, 최대 100)예요. `status`는 `QUEUED`, `SENT`, `DELIVERED`, `FAILED` 중 하나예요. 정렬은 `id` 오름차순이에요.

```json
{
  "page": 0,
  "size": 20,
  "total": 1,
  "items": [
    {
      "id": 15,
      "recipient": "01012345670",
      "carrier": "SKT",
      "channel": "SMS",
      "status": "QUEUED",
      "carrierCode": null,
      "commonCode": null,
      "explanation": null,
      "actionHint": null,
      "fallbackOfId": null,
      "cost": 9
    }
  ]
}
```

`explanation`과 `actionHint`는 `commonCode`가 있거나 상태가 `FAILED`일 때만 채워요.

| commonCode | explanation | actionHint |
|---|---|---|
| `SUCCESS` | 상대가 메시지를 받았어요 | 따로 할 일은 없어요 |
| `INVALID_NUMBER` | 없는 번호라 전달하지 못했어요 | 번호를 확인한 뒤 다시 등록해 주세요 |
| `OPTED_OUT` | 수신을 거부한 번호예요 | 수신거부 목록을 확인해 주세요 |
| `DEVICE_OFF` | 휴대폰이 꺼져 있어 전달하지 못했어요 | 잠시 후 다시 보내 주세요 |
| `SPAM_BLOCKED` | 스팸으로 차단됐어요 | 문구를 바꾼 뒤 다시 보내 주세요 |
| 그 외, 매핑 실패 | 다른 이유로 전달하지 못했어요 | 같은 오류가 반복되면 문의해 주세요 |

## POST /v1/campaigns/{id}/exports

`202`. 파일은 Apache POI `SXSSFWorkbook`(창 100)으로 만들어요.

```json
{
  "id": 4,
  "campaignId": 3,
  "status": "PENDING",
  "downloadUrl": null,
  "error": null
}
```

`status`는 `PENDING`, `RUNNING`, `DONE`, `FAILED`예요. `DONE`이면 `downloadUrl`이 `/v1/exports/{id}/file`이에요.

## GET /v1/exports/{id}

위와 같은 JSON이에요.

## GET /v1/exports/{id}/file

`DONE`일 때만 xlsx를 돌려요. `Content-Type`은 `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`예요. 파일명은 `campaign-results.xlsx`예요. 열은 `번호`, `수신번호`, `통신사`, `채널`, `상태`, `통신사코드`, `공통코드`, `설명`, `다음 조치`, `대체발송 원본`이에요.

## GET /v1/stats

`from`, `to`, `groupBy`가 필요해요. 날짜는 `YYYY-MM-DD`이고 한국 시간으로 양 끝 날을 포함해요. `groupBy`는 `carrier` 또는 `code`예요.

```json
{
  "from": "2026-10-01",
  "to": "2026-10-22",
  "groupBy": "carrier",
  "groups": [
    { "key": "SKT", "total": 10, "failed": 2, "failRate": 0.2 }
  ]
}
```

`code` 그룹의 `key`는 공통 코드예요. 아직 코드가 없으면 `PENDING`이에요.

## GET /v1/alimtalk-templates?clientId=

승인된 템플릿만 돌려줘요.

```json
[
  {
    "id": 1,
    "clientId": 1,
    "templateCode": "WELCOME",
    "body": "안녕 #{name}",
    "variables": ["name"],
    "status": "APPROVED"
  }
]
```

## 발신번호 REST

`POST /v1/sender-numbers` `201`

```json
{ "clientId": 1, "phone": "02-1111-2222" }
```

응답과 `GET /v1/sender-numbers?clientId=`, `GET /v1/sender-numbers/{id}`:

```json
{
  "id": 2,
  "clientId": 1,
  "phone": "0211112222",
  "status": "SUBMITTED",
  "rejectReason": null
}
```

`PATCH /v1/sender-numbers/{id}`

```json
{ "status": "REVIEWING", "rejectReason": null }
```

허용 전이는 `SUBMITTED` → `REVIEWING` → `APPROVED` 또는 `REJECTED`뿐이에요. `REJECTED`는 `rejectReason`이 필요해요. `DELETE`는 `204`예요.

화면은 `GET /sender-numbers?clientId=1`과 `POST /sender-numbers`예요. 상태 버튼은 `POST /sender-numbers/{id}/review`, `/approve`, `/reject`예요. 화면 문구는 `서류 제출`, `심사 중`, `승인`, `반려`예요. 실행 가능한 JAR에서는 JSP가 불안정해서 `bootRun`이나 테스트처럼 `src/main/webapp`이 있는 실행을 쓰세요.

## CORS

`/v1/**`는 `http://localhost:5173`만 허용해요.

## 측정

원본 출력은 `docs/measurements/`에 있어요. 큐 대기는 시뮬레이터 시간이고, 엑셀은 그 프로세스의 힙 사용량이에요.
