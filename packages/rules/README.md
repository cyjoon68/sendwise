# packages/rules

Vitest와 JUnit이 이 디렉터리의 JSON을 같이 읽어요.

저장소 루트에서 확인해요.

```bash
node packages/rules/scripts/check.mjs
```

## 불러오기

경로는 저장소 루트 기준이에요.

- `packages/rules/message-rules.json`
- `packages/rules/ksx1001.json`
- `packages/rules/carrier-codes.json`
- `packages/rules/cases/*.json`

Node에서는 파일을 읽어 `JSON.parse` 해요. `apps/api`에서 JUnit을 돌리면 `../../packages/rules`예요.

케이스 파일은 `[{ "name": string, "input": object, "expected": object }]`예요. `name`은 파일 사이에서 겹치지 않아요.

## message-rules.json

| 키 | 값 |
|---|---|
| `sms.maxBytes` | 90 |
| `lms.maxBytes` | 2000 |
| `ad.blockedHours.from` / `to` | `21:00` / `08:00` |
| `ad.blockedHours.fromInclusive` | true. 21:00부터 막아요 |
| `ad.blockedHours.toInclusive` | false. 08:00은 보낼 수 있어요 |
| `ad.blockedHours.zone` | `Asia/Seoul` |
| `ad.requiredPrefix` | `(광고)` |
| `ad.companyNameRequired` | true. 업체명 문자열은 캠페인 입력이에요 |
| `ad.optOutText` | `무료수신거부 080-000-0000` |
| `unitPrice` | SMS 9, LMS 30, ALIMTALK 7 |
| `encoding` | EUC-KR. ASCII 1바이트, KS X 1001 2바이트 |

## ksx1001.json

| 키 | 내용 |
|---|---|
| `hangul` | 완성형 2,350자. EUC-KR `B0A1`–`C8FE` 순서예요. 구멍은 없어요 |
| `nonHangul` | 두 디코더가 동의한 한글 외 KS X 1001 문자예요 |
| `ascii` | U+0000–U+007F는 1바이트예요 |
| `ksx1001Bytes` | 2 |
| `codecDisagreements` | 디코더가 갈려서 표에서 뺀 바이트예요 |

`hangul`과 `nonHangul`에 없는 비ASCII는 이 표로 보낼 수 없어요. 한자, 기호, 자모는 `nonHangul`에 있으면 2바이트예요.

생성기는 `0xA1A1`–`0xFEFE`를 한 글자씩 풀어요. Python `bytes.decode("euc_kr")`와 Node `TextDecoder("euc-kr")`가 같은 문자를 줄 때만 넣어요. 이번 표는 Node v22.13.0, Python 3.9.6에서 만들었어요. 완성형은 2,350자, `nonHangul`은 5,873자, 뺀 바이트는 191개예요.

뺀 바이트는 아래와 같아요.

- Python만 있음: `A2E6` €(U+20AC), `A2E7` ®(U+00AE)
- Node만 있음: `A4D4` ㅤ(U+3164), `C9A1`–`C9FE` U+E000–U+E05D, `FEA1`–`FEFE` U+E05E–U+E0BB

Node `euc-kr`은 KS X 1001 밖 사용자 영역도 풀어요. 그 문자를 표에 넣지 않았어요.

## 바이트

코드 포인트를 하나씩 봐요. JavaScript는 `for...of`, Java는 `codePointAt`이에요.

1. U+0000–U+007F는 1바이트예요.
2. `hangul` 또는 `nonHangul`에 있으면 2바이트예요.
3. 그 외에는 `encodable: false`, `bytes: null`이에요.

채널은 이렇게 정해요.

- 90바이트 이하: `SMS`
- 91바이트 이상 2000바이트 이하: `LMS`
- 2001바이트 이상: `overLimit: true`, `channel: null`

`똠`, `햏`, `힣`은 2,350자에 없어요. `cases/encoding.json`의 `encodable`은 false예요.

Python 3.9 `euc_kr` `encode`는 이 세 글자를 거절하지 않아요. 자모 4자, 8바이트로 쪼개고, 그 바이트를 다시 풀면 원래 글자가 돼요.

- `똠` → `a4d4a4a8a4c7a4b1`
- `햏` → `a4d4a4bea4c0a4be`
- `힣` → `a4d4a4bea4d3a4be`

완성형 판정에 Python `encode` 성공을 쓰지 마세요. `😀`와 `₩`(U+20A9)는 Python `encode`도 실패해요. `￦`(U+FFE6)는 `A3DC`라서 `nonHangul`에 있어요.

## carrier-codes.json

이 코드는 모의 값이에요. 실제 SKT, KT, LG U+ 결과 코드가 아니에요.

최상위 키는 `mock`, `SKT`, `KT`, `LGU`예요. `mock`은 true예요. 코드는 문자열이고, 값은 공통 코드예요.

공통 코드는 `SUCCESS`, `INVALID_NUMBER`, `OPTED_OUT`, `DEVICE_OFF`, `SPAM_BLOCKED`, `ETC`예요.

체계는 통신사마다 달라요.

- SKT: 숫자 문자열. 예: `"0"`, `"101"`
- KT: 영문으로 시작해요. 예: `"OK"`, `"E101"`
- LGU: 영문 한 글자. 예: `"S"`, `"N"`

표에 없는 코드의 `common`은 null이에요.

## cases

### bytes.json

`input.text` 하나의 바이트와 채널이에요. ASCII 89/90/91, 1999/2000/2001과 완성형 `가`로 맞춘 같은 경계를 넣었어요.

### encoding.json

`expected`는 `encodable`, `bytes`, `inHangul2350`, `unencodable`예요. `inHangul2350`은 모든 코드 포인트가 `hangul` 안에 있을 때만 true예요.

### personalization.json

`#{변수}`를 수신자 값 중 EUC-KR 바이트가 가장 긴 값으로 바꿔요. 그 결과로 채널을 정해요. `shortestBytes`는 가장 짧은 값으로 바꾼 바이트예요.

### ad.json

`scheduledAt`을 `Asia/Seoul` 시:분으로 바꿔요. 21:00 이상이거나 08:00 미만이면 `inBlockedHours`가 true예요.

`purpose`가 `AD`일 때 아래를 모두 만족해야 `allowed`가 true예요.

- 금지 시간이 아님
- 본문이 `(광고)`로 시작함
- 본문에 `companyName`이 있음
- 본문에 `무료수신거부 080-000-0000`이 있음

`purpose`가 `AD`가 아니면 문구를 검사하지 않아요. `allowed`는 true예요. `inBlockedHours`는 시각만 봐요.

### phone.json

1. 앞뒤 공백을 없애요.
2. 공백과 `-`를 없애요.
3. `+82`로 시작하면 그 자리를 `0`으로 바꿔요.
4. `010` 다음 숫자가 8자리면 유효해요. `normalized`는 `01012345678`이에요. 아니면 `valid: false`, `normalized: null`이에요.

### carrier.json

`input.carrier`와 `input.code`로 공통 코드를 찾아요.

## 다시 만들기

문자표를 다시 쓸 때 Node와 Python 3이 필요해요.

```bash
node packages/rules/scripts/generate-ksx1001.mjs
node packages/rules/scripts/check.mjs
```

`check.mjs`는 의존성이 없어요. 파일 모양, 완성형 2,350자, 케이스 `expected`가 표와 맞는지를 봐요.
