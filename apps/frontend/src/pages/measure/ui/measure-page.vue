<script setup lang="ts">
import { MEASURE_ROW_COUNT } from "@/shared/config";
import { parseAndClassify, parseInWorker, type ClassifyResult } from "@/shared/upload";
import { onMounted, ref } from "vue";

type Probe = { maxGapMs: number; turns: number };
type Run = Probe & { totalMs: number; elapsedMs: number; valid: number; invalid: number; duplicate: number; optedOut: number };

const status = ref("idle");
const output = ref("");

const buildCsv = () => {
  const lines = ["전화번호,이름"];
  for (let index = 0; index < MEASURE_ROW_COUNT; index += 1) {
    if (index % 1000 === 1) {
      lines.push("bad,오류");
      continue;
    }
    if (index % 1000 === 2) {
      lines.push("01000000000,거부");
      continue;
    }
    if (index % 1000 === 3) {
      lines.push("01010000000,중복");
      continue;
    }
    lines.push(`010${String(10000000 + index).slice(-8)},사용자${index % 40}`);
  }
  return lines.join("\n");
};

const startProbe = () => {
  let last = performance.now();
  let maxGap = 0;
  let turns = 0;
  let running = true;
  const timer = window.setInterval(() => {
    const now = performance.now();
    maxGap = Math.max(maxGap, now - last);
    last = now;
  }, 20);
  const spin = () => {
    if (!running) return;
    turns += 1;
    window.setTimeout(spin, 0);
  };
  spin();
  return (): Probe => {
    running = false;
    window.clearInterval(timer);
    maxGap = Math.max(maxGap, performance.now() - last);
    return { maxGapMs: Math.round(maxGap), turns };
  };
};

const summarize = (result: ClassifyResult, totalMs: number, elapsedMs: number, probe: Probe): Run => ({
  ...probe,
  totalMs: Math.round(totalMs),
  elapsedMs: Math.round(elapsedMs),
  valid: result.valid.length,
  invalid: result.invalid.length,
  duplicate: result.duplicate.length,
  optedOut: result.optedOut.length,
});

const run = async () => {
  status.value = "running";
  const csv = buildCsv();
  const optOuts = ["010-0000-0000"];
  const encoded = new TextEncoder().encode(csv);
  const stopMain = startProbe();
  const mainStarted = performance.now();
  const mainResult = parseAndClassify(encoded, optOuts);
  const mainProbe = stopMain();
  const main = summarize(mainResult, performance.now() - mainStarted, performance.now() - mainStarted, mainProbe);
  await new Promise((resolve) => window.setTimeout(resolve, 50));
  const stopWorker = startProbe();
  const workerStarted = performance.now();
  const fileBytes = encoded.buffer.slice(encoded.byteOffset, encoded.byteOffset + encoded.byteLength);
  const workerPayload = await parseInWorker(fileBytes, optOuts);
  const workerProbe = stopWorker();
  const worker = summarize(
    workerPayload.result,
    performance.now() - workerStarted,
    workerPayload.elapsedMs,
    workerProbe,
  );
  const report = {
    rows: MEASURE_ROW_COUNT,
    file: "csv",
    userAgent: navigator.userAgent,
    hardwareConcurrency: navigator.hardwareConcurrency,
    main,
    worker,
  };
  output.value = JSON.stringify(report, null, 2);
  status.value = main.valid === worker.valid && main.invalid === worker.invalid ? "done" : "mismatch";
};

onMounted(() => {
  if (new URLSearchParams(window.location.search).get("auto") === "1") void run();
});
</script>

<template>
  <main class="stack measure">
    <h1>수신자 업로드 측정</h1>
    <button type="button" @click="run">측정 시작</button>
    <pre id="measure-result" :data-status="status">{{ output }}</pre>
  </main>
</template>
