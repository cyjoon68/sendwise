<script setup lang="ts">
import { api, type StatsBucket } from "@/shared/api";
import { onMounted, ref } from "vue";
import FailureChart from "./failure-chart.vue";

const dayOffset = (days: number) => {
  const date = new Date(Date.now() + days * 24 * 60 * 60 * 1000);
  return new Intl.DateTimeFormat("en-CA", { timeZone: "Asia/Seoul", year: "numeric", month: "2-digit", day: "2-digit" }).format(date);
};

const from = ref(dayOffset(-7));
const to = ref(dayOffset(0));
const carrier = ref<{ key: string; failureRate: number }[]>([]);
const code = ref<{ key: string; failureRate: number }[]>([]);

const toItems = (groups: StatsBucket[]) => groups.map((group) => ({
  key: group.key,
  failureRate: group.failRate,
}));

const load = async () => {
  const [byCarrier, byCode] = await Promise.all([
    api.stats({ from: from.value, to: to.value, groupBy: "carrier" }),
    api.stats({ from: from.value, to: to.value, groupBy: "code" }),
  ]);
  carrier.value = toItems(byCarrier.groups);
  code.value = toItems(byCode.groups);
};

onMounted(load);
</script>

<template>
  <section class="stack">
    <h1>통계</h1>
    <form class="row" @submit.prevent="load">
      <label>시작 <input v-model="from" type="date" /></label>
      <label>끝 <input v-model="to" type="date" /></label>
      <button type="submit">보기</button>
    </form>
    <FailureChart title="통신사별 실패율" :items="carrier" />
    <FailureChart title="공통 코드별 실패율" :items="code" />
  </section>
</template>
