<script setup lang="ts">
defineProps<{
  text: string;
  caption: string;
}>();
</script>

<template>
  <div class="phone">
    <div class="phone-screen">
      <p class="phone-caption">{{ caption }}</p>
      <p class="bubble">{{ text || "미리볼 내용이 없어요" }}</p>
    </div>
  </div>
</template>
