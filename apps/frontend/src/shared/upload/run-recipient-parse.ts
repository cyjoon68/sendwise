import type { ClassifyResult } from "./parse-recipients";
import RecipientWorker from "./recipient-worker?worker";

export const parseInWorker = (buffer: ArrayBuffer, optOuts: readonly string[]) =>
  new Promise<{ result: ClassifyResult; elapsedMs: number }>((resolve, reject) => {
    const worker = new RecipientWorker();
    const copy = buffer.slice(0);
    worker.onmessage = (event: MessageEvent<{ result: ClassifyResult; elapsedMs: number }>) => {
      worker.terminate();
      resolve(event.data);
    };
    worker.onerror = () => {
      worker.terminate();
      reject(new Error("수신자 파일을 읽지 못했어요"));
    };
    worker.postMessage({ buffer: copy, optOuts: [...optOuts] }, [copy]);
  });
