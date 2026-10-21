<script setup lang="ts">
import { useSessionStore } from "@/shared/auth";
import { formatWon } from "@/shared/lib";
import { computed } from "vue";

const session = useSessionStore();
const balanceLabel = computed(() => (session.balance === null
  ? "잔액은 발송한 뒤에 확인할 수 있어요"
  : `잔액 ${formatWon(session.balance)}`));
</script>

<template>
  <section class="stack">
    <header class="page-head">
      <h1>고객사 {{ session.clientId }}</h1>
      <p class="balance-large">{{ balanceLabel }}</p>
    </header>
    <router-link class="button" to="/compose">발송하기</router-link>
  </section>
</template>
