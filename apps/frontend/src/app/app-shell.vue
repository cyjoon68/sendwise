<script setup lang="ts">
import { useSessionStore } from "@/shared/auth";
import { formatWon } from "@/shared/lib";
import { computed } from "vue";
import { RouterView } from "vue-router";

const session = useSessionStore();
const balanceLabel = computed(() => (session.balance === null ? "" : formatWon(session.balance)));
</script>

<template>
  <div class="shell">
    <header class="topbar">
      <router-link class="brand" to="/">센드와이즈</router-link>
      <nav class="nav">
        <router-link to="/">대시보드</router-link>
        <router-link to="/compose">발송</router-link>
        <router-link to="/sender-numbers">발신번호</router-link>
        <router-link to="/stats">통계</router-link>
      </nav>
      <p class="session-meta">고객사 {{ session.clientId }}<template v-if="balanceLabel"> · {{ balanceLabel }}</template></p>
    </header>
    <main class="content">
      <RouterView />
    </main>
  </div>
</template>
