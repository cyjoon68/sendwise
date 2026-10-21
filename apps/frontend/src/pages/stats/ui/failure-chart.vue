<script setup lang="ts">
import { commonCodeCopy, isCommonCode } from "@/shared/message-rules";
import * as echarts from "echarts";
import { onMounted, onUnmounted, ref, watch } from "vue";

const props = defineProps<{
  title: string;
  items: { key: string; failureRate: number }[];
}>();

const host = ref<HTMLDivElement | null>(null);
let chart: echarts.ECharts | null = null;

const labelOf = (key: string) => {
  if (key === "LGU") return "LG U+";
  if (isCommonCode(key)) return commonCodeCopy[key].label;
  return key;
};

const render = () => {
  if (!host.value || props.items.length === 0) {
    chart?.clear();
    return;
  }
  chart ??= echarts.init(host.value);
  chart.setOption({
    grid: { left: 48, right: 12, top: 16, bottom: 32 },
    xAxis: { type: "category", data: props.items.map((item) => labelOf(item.key)) },
    yAxis: { type: "value", max: 100, axisLabel: { formatter: "{value}%" } },
    series: [{
      type: "bar",
      data: props.items.map((item) => Math.round(item.failureRate * 1000) / 10),
      itemStyle: { color: "#1f6feb" },
    }],
    tooltip: { valueFormatter: (value: number) => `${value}%` },
  });
};

const onResize = () => chart?.resize();

onMounted(() => {
  render();
  window.addEventListener("resize", onResize);
});

watch(() => props.items, render, { deep: true });

onUnmounted(() => {
  window.removeEventListener("resize", onResize);
  chart?.dispose();
});
</script>

<template>
  <article class="card stack">
    <h2>{{ title }}</h2>
    <p v-if="items.length === 0">이 기간에는 실패가 없어요.</p>
    <div v-show="items.length > 0" ref="host" class="chart"></div>
  </article>
</template>
