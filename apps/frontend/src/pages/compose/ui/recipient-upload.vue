<script setup lang="ts">
import { invalidWorkbook, parseInWorker, type ClassifyResult } from "@/shared/upload";
import { ref } from "vue";
import RecipientGroups from "./recipient-groups.vue";

const props = defineProps<{
  optOuts: string[];
  parseFile?: (data: ArrayBuffer, optOuts: string[]) => Promise<ClassifyResult>;
}>();

const emit = defineEmits<{
  parsed: [result: ClassifyResult];
}>();

const result = ref<ClassifyResult | null>(null);
const error = ref("");
const reading = ref(false);

const parseBuffer = async (buffer: ArrayBuffer) => {
  if (props.parseFile) return props.parseFile(buffer, props.optOuts);
  const parsed = await parseInWorker(buffer, props.optOuts);
  return parsed.result;
};

const onFile = async (event: Event) => {
  const file = (event.target as HTMLInputElement).files?.[0];
  if (!file) return;
  reading.value = true;
  error.value = "";
  try {
    const parsed = await parseBuffer(await file.arrayBuffer());
    result.value = parsed;
    emit("parsed", parsed);
  } catch {
    error.value = "파일을 읽지 못했어요. xlsx 또는 csv인지 확인해 주세요.";
  } finally {
    reading.value = false;
  }
};

const download = () => {
  if (!result.value || result.value.invalid.length === 0) return;
  const bytes = invalidWorkbook(result.value.invalid);
  const copy = new ArrayBuffer(bytes.byteLength);
  new Uint8Array(copy).set(bytes);
  const blob = new Blob([copy], { type: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" });
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = "오류-행.xlsx";
  link.click();
  URL.revokeObjectURL(url);
};

</script>

<template>
  <div class="stack">
    <label class="stack">
      수신자 파일
      <input type="file" accept=".xlsx,.xls,.csv,text/csv" @change="onFile" />
    </label>
    <p v-if="reading">파일을 읽고 있어요.</p>
    <p v-if="error" class="danger">{{ error }}</p>
    <RecipientGroups v-if="result" :result="result" @download="download" />
  </div>
</template>
