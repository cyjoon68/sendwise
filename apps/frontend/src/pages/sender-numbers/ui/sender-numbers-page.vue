<script setup lang="ts">
import { ApiError, api, type SenderNumber, type SenderStatus } from "@/shared/api";
import { onMounted, ref } from "vue";

const numbers = ref<SenderNumber[]>([]);
const draft = ref("");
const error = ref("");

const labels: Record<SenderStatus, string> = {
  SUBMITTED: "서류 제출",
  REVIEWING: "심사 중",
  APPROVED: "승인",
  REJECTED: "반려",
};

const load = async () => {
  numbers.value = await api.senderNumbers();
};

const create = async () => {
  error.value = "";
  try {
    await api.createSenderNumber(draft.value);
    draft.value = "";
    await load();
  } catch (reason) {
    error.value = reason instanceof ApiError ? reason.message : "등록하지 못했어요";
  }
};

const review = async (id: number) => {
  await api.reviewSenderNumber(id);
  await load();
};

const remove = async (id: number) => {
  await api.deleteSenderNumber(id);
  await load();
};

onMounted(load);
</script>

<template>
  <section class="stack">
    <h1>발신번호</h1>
    <form class="card row" @submit.prevent="create">
      <label class="stack grow">
        새 발신번호
        <input v-model="draft" type="text" placeholder="02-1588-0000" />
      </label>
      <button type="submit">서류 제출</button>
    </form>
    <p v-if="error" class="danger">{{ error }}</p>
    <table class="table">
      <thead>
        <tr>
          <th>번호</th>
          <th>상태</th>
          <th>반려 사유</th>
          <th></th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="item in numbers" :key="item.id">
          <td>{{ item.phone }}</td>
          <td>{{ labels[item.status] }}</td>
          <td>{{ item.rejectReason }}</td>
          <td class="row">
            <button v-if="item.status === 'SUBMITTED'" type="button" @click="review(item.id)">심사 요청</button>
            <button type="button" @click="remove(item.id)">삭제</button>
          </td>
        </tr>
      </tbody>
    </table>
  </section>
</template>
