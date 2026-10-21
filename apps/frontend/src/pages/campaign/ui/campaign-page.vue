<script setup lang="ts">
import { api, type CampaignDetail, type CampaignMessage, type ExportJob } from "@/shared/api";
import { computed, onMounted, ref, watch } from "vue";
import { useRoute } from "vue-router";

const route = useRoute();
const campaign = ref<CampaignDetail | null>(null);
const page = ref(0);
const total = ref(0);
const size = ref(10);
const messages = ref<CampaignMessage[]>([]);
const job = ref<ExportJob | null>(null);
const fileUrl = ref("");
const error = ref("");

const pageCount = computed(() => Math.max(1, Math.ceil(total.value / size.value)));
const pages = computed(() => Array.from({ length: pageCount.value }, (_, index) => index));
const campaignId = () => Number(route.params.id);

const loadMessages = async () => {
  const response = await api.messages(campaignId(), page.value);
  messages.value = response.items;
  total.value = response.total;
  size.value = response.size;
};

const load = async () => {
  error.value = "";
  try {
    campaign.value = await api.campaign(campaignId());
    await loadMessages();
  } catch {
    error.value = "캠페인을 불러오지 못했어요.";
  }
};

const startExport = async () => {
  if (!campaign.value) return;
  fileUrl.value = "";
  job.value = await api.startExport(campaign.value.id);
};

const refreshExport = async () => {
  if (!job.value) return;
  job.value = await api.exportStatus(job.value.id);
  if (job.value.status !== "DONE") return;
  const bytes = await api.exportFile(job.value.id);
  const copy = new ArrayBuffer(bytes.byteLength);
  new Uint8Array(copy).set(bytes);
  const blob = new Blob([copy], { type: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" });
  if (fileUrl.value) URL.revokeObjectURL(fileUrl.value);
  fileUrl.value = URL.createObjectURL(blob);
};

watch(page, () => {
  void loadMessages();
});

onMounted(load);
</script>

<template>
  <section v-if="campaign" class="stack">
    <header class="page-head">
      <h1>캠페인 결과</h1>
      <p>대기 {{ campaign.counts.QUEUED }}건 · 전송 {{ campaign.counts.SENT }}건 · 성공 {{ campaign.counts.DELIVERED }}건 · 실패 {{ campaign.counts.FAILED }}건 · 대체 발송 {{ campaign.fallbackCount }}건</p>
    </header>
    <section class="stack">
      <h2>실패한 문자</h2>
      <p v-if="messages.length === 0">실패한 문자가 없어요.</p>
      <table v-else class="table">
        <thead>
          <tr>
            <th>수신번호</th>
            <th>통신사</th>
            <th>공통 코드</th>
            <th>설명</th>
            <th>조치</th>
            <th>대체 발송</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="item in messages" :key="item.id">
            <td>{{ item.recipient }}</td>
            <td>{{ item.carrier === "LGU" ? "LG U+" : item.carrier }}</td>
            <td>{{ item.commonCode ?? "없음" }}</td>
            <td>{{ item.explanation }}</td>
            <td>{{ item.actionHint }}</td>
            <td>{{ item.fallbackOfId === null ? "없음" : `원본 ${item.fallbackOfId}` }}</td>
          </tr>
        </tbody>
      </table>
      <nav class="pager" aria-label="페이지">
        <button type="button" :disabled="page <= 0" @click="page -= 1">이전</button>
        <button
          v-for="number in pages"
          :key="number"
          type="button"
          :aria-current="number === page ? 'page' : undefined"
          @click="page = number"
        >
          {{ number + 1 }}
        </button>
        <button type="button" :disabled="page >= pageCount - 1" @click="page += 1">다음</button>
      </nav>
    </section>
    <div class="row">
      <button type="button" @click="startExport">결과 엑셀 받기</button>
      <button v-if="job && (job.status === 'PENDING' || job.status === 'RUNNING')" type="button" @click="refreshExport">상태 확인</button>
      <a v-if="fileUrl" :href="fileUrl" download="campaign-results.xlsx">파일 받기</a>
    </div>
    <p v-if="job?.status === 'PENDING' || job?.status === 'RUNNING'">만드는 중이에요. 상태 확인을 눌러 주세요.</p>
    <p v-if="job?.status === 'FAILED'" class="danger">{{ job.error }}</p>
  </section>
  <p v-else-if="error" class="danger">{{ error }}</p>
  <p v-else>불러오는 중이에요.</p>
</template>
