# 병렬 작업 분담

기준 문서: `docs/plan.md`. 각 세션은 자기 영역 디렉터리만 수정해요. 루트 파일(`settings.gradle`, `package.json`, `compose.yaml`, `.github/`)은 수정하지 않고, 필요하면 PR 설명에 적어요.

| 세션 | 브랜치 | 소유 디렉터리 | 범위 (plan.md 작업 분해 번호) |
|---|---|---|---|
| rules | `feat/rules` | `packages/rules/` | 3번 중 규칙 데이터: `message-rules.json`, `ksx1001.json`, `carrier-codes.json`, `cases/*.json`, `README.md`(스키마 설명) |
| api | `feat/api` | `apps/api/` | 2, 6(JSP), 8(서버), 9, 11, 12(서버) |
| carrier | `feat/carrier-mock` | `apps/carrier-mock/` | 모의 SKT/KT/LGU+, 모의 알림톡 (plan.md 6-8, 3장 문제 9, 10) |
| frontend | `feat/frontend` | `apps/frontend/` | 3(TS 모듈), 4, 5, 7, 8(화면), 10, 12(Vue 화면) |

## 계약

- API: `docs/plan.md` 10장 API 명세가 기준이에요. 요청/응답 필드는 api 세션이 `apps/api/API.md`에 적고, frontend 세션은 그 문서가 없으면 plan.md 기준 타입으로 작성해요.
- 규칙 데이터: `packages/rules/`의 JSON 스키마는 rules 세션이 정해요. api, frontend 세션은 아래 경로와 최상위 키를 가정해요.
  - `message-rules.json`: `{ "sms": {"maxBytes": 90}, "lms": {"maxBytes": 2000}, "ad": {"blockedHours": {"from": "21:00", "to": "08:00"}, "requiredPrefix": "(광고)", "optOutText": "무료수신거부 080-000-0000"}, "unitPrice": {"SMS": 9, "LMS": 30, "ALIMTALK": 7} }`
  - `ksx1001.json`: `{ "hangul": "가각간..." }` (완성형 한글 2,350자 문자열)
  - `carrier-codes.json`: `{ "SKT": {"<code>": "<COMMON_CODE>"}, "KT": {...}, "LGU": {...} }`, 공통 코드: `SUCCESS, INVALID_NUMBER, OPTED_OUT, DEVICE_OFF, SPAM_BLOCKED, ETC`
  - `cases/*.json`: `[{ "name": "...", "input": {...}, "expected": {...} }]`
- carrier-mock ↔ api: 발송 `POST /send` (carrier-mock), 결과 콜백 `POST /carrier/{carrier}/results` (api). 필드는 carrier 세션이 `apps/carrier-mock/README.md`에 적어요.

## 공통 규칙

- 스택: frontend는 Vue 3 + TypeScript + Vite + Pinia + Vue Router + VeeValidate + SheetJS + ECharts + Vitest + Vue Testing Library + Cypress. 백엔드는 Java 21 + Spring Boot 3 + Gradle(Groovy DSL) + Spring Data JPA + MySQL 8 + JUnit 5 + Testcontainers.
- 측정값은 실제로 잰 값만 `docs/measurements/`에 명령어, 환경, 원본 출력과 함께 남겨요. 지어낸 수치를 쓰지 않아요.
- 커밋은 Conventional Commits(`feat(api): ...`), 작은 단위로 나눠요.
- 끝나면 자기 브랜치를 push하고 `main` 대상 PR을 열어요. 머지는 하지 않아요.
