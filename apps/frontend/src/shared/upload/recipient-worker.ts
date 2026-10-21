import { parseAndClassify, type ClassifyResult } from "./parse-recipients";

const scope = self as unknown as {
  onmessage: ((event: MessageEvent<{ buffer: ArrayBuffer; optOuts: string[] }>) => void) | null;
  postMessage: (data: unknown) => void;
};

scope.onmessage = (event) => {
  const started = performance.now();
  const result: ClassifyResult = parseAndClassify(event.data.buffer, event.data.optOuts);
  scope.postMessage({ result, elapsedMs: performance.now() - started });
};
