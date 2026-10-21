<script setup lang="ts">
defineProps<{
  modelValue: string;
  parts: { text: string; outside: boolean }[];
  meter: string;
  note: string;
  suggestions: { char: string; suggestion: string }[];
  label: string;
}>();

const emit = defineEmits<{
  "update:modelValue": [value: string];
  replace: [from: string, to: string];
}>();

const onInput = (event: Event) => {
  emit("update:modelValue", (event.target as HTMLTextAreaElement).value);
};

const onScroll = (event: Event) => {
  const target = event.target as HTMLTextAreaElement;
  const backdrop = target.previousElementSibling as HTMLElement | null;
  if (backdrop) backdrop.scrollTop = target.scrollTop;
};
</script>

<template>
  <div class="stack">
    <label class="stack">
      <span>{{ label }}</span>
      <span class="editor">
        <span class="editor-backdrop" aria-hidden="true">
          <span v-for="(part, index) in parts" :key="index" :class="{ outside: part.outside }">{{ part.text }}</span>
        </span>
        <textarea :value="modelValue" rows="6" spellcheck="false" @input="onInput" @scroll="onScroll"></textarea>
      </span>
    </label>
    <p class="meter" aria-live="polite">{{ meter }}</p>
    <p class="note">{{ note }}</p>
    <ul v-if="suggestions.length > 0" class="suggestions">
      <li v-for="item in suggestions" :key="item.char">
        <button type="button" @click="emit('replace', item.char, item.suggestion)">
          {{ item.char }} → {{ item.suggestion }}로 바꾸기
        </button>
      </li>
    </ul>
  </div>
</template>
