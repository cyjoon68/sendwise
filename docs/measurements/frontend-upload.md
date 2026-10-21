# 수신자 10만 건 업로드 측정

같은 CSV 버퍼를 메인 스레드 `parseAndClassify`와 워커에 각각 한 번씩 넣었습니다. 파일은 CSV입니다. xlsx로 만들면 파일 생성 시간이 측정에 섞입니다. 제품은 두 형식을 모두 받습니다.

## 환경

- 시각: 2026-10-22 03:02 KST (`2026-10-21T18:02:27.209Z`)
- OS: Darwin 25.4.0
- Node: v22.13.0
- 브라우저: Google Chrome for Testing 148.0.7778.97, headless
- 이 맥에는 `/Applications/Google Chrome.app`이 없어서 Chrome for Testing을 썼습니다.
- `hardwareConcurrency`: 10
- 서버: Vite 6.4.5 dev, `http://127.0.0.1:5173/measure?auto=1`
- 명령:

```bash
cd apps/frontend
./node_modules/.bin/vite --host 127.0.0.1 --port 5173 --strictPort
node scripts/measure-upload.mjs
```

프로브는 20ms `setInterval`의 최대 간격과, 멈춘 뒤 마지막 간격입니다. `turns`는 `setTimeout(0)`이 돈 횟수입니다. `elapsedMs`는 파싱 함수 안의 시간입니다. 워커 `totalMs`에는 버퍼를 워커로 복사하는 시간이 포함됩니다.

## 결과

상태: `done`. 메인과 워커의 건수가 같습니다.

| | 메인 | 워커 |
| --- | --- | --- |
| totalMs | 253 | 318 |
| elapsedMs | 253 | 241 |
| maxGapMs | 253 | 36 |
| turns | 1 | 67 |
| valid | 99700 | 99700 |
| invalid | 100 | 100 |
| duplicate | 100 | 100 |
| optedOut | 100 | 100 |

메인은 파싱하는 동안 이벤트 루프가 한 번도 돌지 않았습니다. 최대 간격이 전체 시간과 같습니다. 워커는 파싱 중에도 타이머가 67번 돌았고, 최대 간격은 36ms입니다. 전체 시간은 워커가 318ms로 메인 253ms보다 깁니다. 차이는 스레드로 버퍼를 넘기는 시간입니다.

## 원본 JSON

```json
{
  "rows": 100000,
  "file": "csv",
  "userAgent": "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) HeadlessChrome/148.0.0.0 Safari/537.36",
  "hardwareConcurrency": 10,
  "main": {
    "maxGapMs": 253,
    "turns": 1,
    "totalMs": 253,
    "elapsedMs": 253,
    "valid": 99700,
    "invalid": 100,
    "duplicate": 100,
    "optedOut": 100
  },
  "worker": {
    "maxGapMs": 36,
    "turns": 67,
    "totalMs": 318,
    "elapsedMs": 241,
    "valid": 99700,
    "invalid": 100,
    "duplicate": 100,
    "optedOut": 100
  }
}
```
