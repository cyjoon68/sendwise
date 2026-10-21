<script setup lang="ts">
import { SAMPLE_LIMIT } from "@/shared/config";
import type { ClassifyResult, RecipientRow } from "@/shared/upload";

defineProps<{
  result: ClassifyResult;
}>();

const emit = defineEmits<{
  download: [];
}>();

const samples = (rows: RecipientRow[]) => rows.slice(0, SAMPLE_LIMIT);
</script>

<template>
  <div class="groups">
    <section data-testid="group-valid">
      <h3>유효한 번호</h3>
      <p>{{ result.valid.length }}건</p>
      <ul>
        <li v-for="row in samples(result.valid)" :key="row.phone ?? row.raw">{{ row.phone }}</li>
      </ul>
    </section>
    <section data-testid="group-invalid">
      <h3>형식이 틀린 번호</h3>
      <p>{{ result.invalid.length }}건</p>
      <ul>
        <li v-for="row in samples(result.invalid)" :key="row.raw">{{ row.raw }}</li>
      </ul>
    </section>
    <section data-testid="group-duplicate">
      <h3>중복된 번호</h3>
      <p>{{ result.duplicate.length }}건</p>
      <ul>
        <li v-for="row in samples(result.duplicate)" :key="`${row.phone}-dup`">{{ row.phone }}</li>
      </ul>
    </section>
    <section data-testid="group-opt-out">
      <h3>수신거부 번호</h3>
      <p>{{ result.optedOut.length }}건</p>
      <ul>
        <li v-for="row in samples(result.optedOut)" :key="`${row.phone}-out`">{{ row.phone }}</li>
      </ul>
    </section>
  </div>
  <button v-if="result.invalid.length > 0" type="button" @click="emit('download')">오류 행 엑셀로 받기</button>
</template>
