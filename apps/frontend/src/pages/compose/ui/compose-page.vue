<script setup lang="ts">
import { ApiError, api, type AlimtalkTemplate, type FallbackMode, type Purpose, type SenderNumber } from "@/shared/api";
import { useSessionStore } from "@/shared/auth";
import { formatWon } from "@/shared/lib";
import { messageRules } from "@/shared/message-rules";
import type { ClassifyResult } from "@/shared/upload";
import { useField, useForm } from "vee-validate";
import { computed, onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import { deriveCompose, scheduledIso } from "../model/compose-view";
import { mismatchParts } from "../model/template-match";
import BodyEditor from "./body-editor.vue";
import PhonePreview from "./phone-preview.vue";
import RecipientUpload from "./recipient-upload.vue";

const props = defineProps<{
  parseFile?: (data: ArrayBuffer, optOuts: string[]) => Promise<ClassifyResult>;
}>();

const router = useRouter();
const session = useSessionStore();
const { handleSubmit } = useForm();
const { value: body } = useField<string>("body", (value) => {
  if (mode.value === "TEXT" && String(value ?? "").trim() === "") return "본문을 입력해 주세요";
  return true;
}, { initialValue: "" });
const { value: companyName, errorMessage: companyError } = useField<string>("companyName", (value) => {
  if (purpose.value === "AD" && String(value ?? "").trim() === "") return "업체명을 입력해 주세요";
  return true;
}, { initialValue: "" });
const { value: senderNumber, errorMessage: senderError } = useField<string>("senderNumber", (value) => {
  if (!value) return "발신번호를 선택해 주세요";
  return true;
}, { initialValue: "" });

const mode = ref<"TEXT" | "ALIMTALK">("TEXT");
const purpose = ref<Purpose>("NORMAL");
const scheduledAt = ref("");
const templateCode = ref("");
const templateBody = ref("");
const fallback = ref<FallbackMode>("NONE");
const fallbackBody = ref("");
const recipients = ref<ClassifyResult | null>(null);
const senders = ref<SenderNumber[]>([]);
const templates = ref<AlimtalkTemplate[]>([]);
const sending = ref(false);
const sendError = ref("");
const rulesDiffer = ref(false);
const balanceLabel = computed(() => (session.balance === null
  ? "잔액은 발송한 뒤에 확인할 수 있어요"
  : `잔액 ${formatWon(session.balance)}`));

const derived = computed(() => deriveCompose({
  mode: mode.value,
  purpose: purpose.value,
  body: body.value,
  companyName: companyName.value,
  scheduledAt: scheduledAt.value,
  senderNumber: senderNumber.value,
  templateBody: templateBody.value,
  fallback: fallback.value,
  fallbackBody: fallbackBody.value,
  recipientCount: recipients.value?.valid.length ?? 0,
  variableValues: recipients.value?.variables ?? {},
  previewVariables: recipients.value?.valid[0]?.variables ?? {},
  balance: session.balance,
  nowIso: new Date().toISOString(),
}));

const editorParts = computed(() => {
  if (mode.value !== "ALIMTALK" || templateBody.value === "") return derived.value.parts;
  return mismatchParts(body.value, derived.value.templateMismatchIndex).map((part) => ({
    text: part.text,
    outside: part.bad,
  }));
});

const replaceBody = (from: string, to: string) => {
  body.value = body.value.split(from).join(to);
};

const replaceFallback = (from: string, to: string) => {
  fallbackBody.value = fallbackBody.value.split(from).join(to);
};

const applyTemplate = () => {
  const template = templates.value.find((item) => item.templateCode === templateCode.value);
  templateBody.value = template?.body ?? "";
  if (template) body.value = template.body;
};

const onParsed = (result: ClassifyResult) => {
  recipients.value = result;
};

onMounted(async () => {
  const [remoteRules, numbers, templateList] = await Promise.all([
    api.rules(),
    api.senderNumbers(),
    api.templates(),
  ]);
  rulesDiffer.value = remoteRules.sms.maxBytes !== messageRules.sms.maxBytes
    || remoteRules.unitPrice.LMS !== messageRules.unitPrice.LMS;
  senders.value = numbers.filter((item) => item.status === "APPROVED");
  templates.value = templateList;
});

const submit = handleSubmit(async () => {
  const view = derived.value;
  const channel = view.channel;
  if (view.blockReason || (channel !== "SMS" && channel !== "LMS" && channel !== "ALIMTALK")) return;
  sending.value = true;
  sendError.value = "";
  try {
    const response = await api.createCampaign({
      clientId: session.clientId,
      channel,
      purpose: purpose.value,
      body: purpose.value === "AD" && mode.value === "TEXT" ? view.fullBody : body.value,
      fallback: mode.value === "ALIMTALK" ? fallback.value : "NONE",
      fallbackBody: mode.value === "ALIMTALK" && fallback.value === "CUSTOM" ? fallbackBody.value : null,
      senderNumber: senderNumber.value,
      templateCode: mode.value === "ALIMTALK" ? templateCode.value : null,
      companyName: purpose.value === "AD" ? companyName.value.trim() : null,
      scheduledAt: scheduledAt.value ? scheduledIso(scheduledAt.value, new Date().toISOString()) : null,
      recipients: (recipients.value?.valid ?? []).map((row) => ({
        phone: row.phone ?? "",
        variables: row.variables,
      })),
    });
    session.apply({ clientId: session.clientId, balance: response.balanceAfter });
    await router.push(`/campaigns/${response.id}`);
  } catch (error) {
    if (error instanceof ApiError && error.balance !== undefined) {
      session.apply({ clientId: session.clientId, balance: error.balance });
    }
    sendError.value = error instanceof ApiError ? error.message : "보내지 못했어요";
  } finally {
    sending.value = false;
  }
});
</script>

<template>
  <form class="compose" @submit.prevent="submit">
    <div class="stack">
      <header class="page-head">
        <h1>발송</h1>
        <p v-if="rulesDiffer" class="danger">서버 규칙과 화면 규칙이 달라요.</p>
      </header>
      <fieldset class="choice">
        <legend>채널</legend>
        <label><input type="radio" name="channel" value="SMS" :checked="mode === 'TEXT' && derived.channel === 'SMS'" @change="mode = 'TEXT'" /> SMS</label>
        <label><input type="radio" name="channel" value="LMS" :checked="mode === 'TEXT' && (derived.channel === 'LMS' || derived.overLimit)" @change="mode = 'TEXT'" /> LMS</label>
        <label><input type="radio" name="channel" value="ALIMTALK" :checked="mode === 'ALIMTALK'" @change="mode = 'ALIMTALK'" /> 알림톡</label>
      </fieldset>
      <fieldset class="choice">
        <legend>목적</legend>
        <label><input v-model="purpose" type="radio" value="NORMAL" /> 일반</label>
        <label><input v-model="purpose" type="radio" value="AD" /> 광고</label>
        <label><input v-model="purpose" type="radio" value="AUTH" /> 인증</label>
      </fieldset>
      <label v-if="purpose === 'AD' && mode === 'TEXT'" class="stack">
        업체명
        <input v-model="companyName" type="text" />
        <span v-if="companyError" class="danger">{{ companyError }}</span>
      </label>
      <p v-if="purpose === 'AD' && mode === 'TEXT'" class="fixed-block">{{ messageRules.ad.requiredPrefix }}{{ companyName || "업체명" }}</p>
      <BodyEditor
        v-model="body"
        :parts="editorParts"
        :meter="mode === 'TEXT' ? derived.meter : '알림톡은 문자 바이트로 과금하지 않아요.'"
        :note="mode === 'TEXT' ? derived.priceNote : '변수는 #{name} 모양으로 두세요.'"
        :suggestions="mode === 'TEXT' ? derived.suggestions : []"
        label="본문"
        @replace="replaceBody"
      />
      <p v-if="purpose === 'AD' && mode === 'TEXT'" class="fixed-block">{{ messageRules.ad.optOutText }}</p>
      <section v-if="mode === 'ALIMTALK'" class="card stack">
        <label class="stack">
          템플릿
          <select v-model="templateCode" @change="applyTemplate">
            <option value="">선택</option>
            <option v-for="item in templates" :key="item.id" :value="item.templateCode">{{ item.templateCode }}</option>
          </select>
        </label>
        <p v-if="templateCode && !derived.templateOk" class="danger">템플릿과 다른 부분이 있어요.</p>
        <fieldset class="choice">
          <legend>대체 발송</legend>
          <label><input v-model="fallback" type="radio" value="NONE" /> 안 해요</label>
          <label><input v-model="fallback" type="radio" value="SAME" /> 같은 문구</label>
          <label><input v-model="fallback" type="radio" value="CUSTOM" /> 다른 문구</label>
        </fieldset>
        <p v-if="fallback === 'SAME'" class="note">{{ derived.fallbackMeter }} {{ derived.fallbackNote }}</p>
        <BodyEditor
          v-if="fallback === 'CUSTOM'"
          v-model="fallbackBody"
          :parts="derived.fallbackParts"
          :meter="derived.fallbackMeter"
          :note="derived.fallbackNote"
          :suggestions="derived.fallbackSuggestions"
          label="대체 문자"
          @replace="replaceFallback"
        />
      </section>
      <RecipientUpload :opt-outs="[]" :parse-file="props.parseFile" @parsed="onParsed" />
      <label class="stack">
        발신번호
        <select v-model="senderNumber">
          <option value="">선택</option>
          <option v-for="item in senders" :key="item.id" :value="item.phone">{{ item.phone }}</option>
        </select>
        <span v-if="senderError" class="danger">{{ senderError }}</span>
        <router-link v-if="senders.length === 0" to="/sender-numbers">승인된 번호가 없어요. 발신번호 관리로 이동</router-link>
      </label>
      <label class="stack">
        예약 시각
        <input v-model="scheduledAt" type="datetime-local" />
        <span class="note">비워 두면 지금 보내요. 한국 시간이에요.</span>
      </label>
      <section class="card stack" data-testid="cost-bar">
        <h2>예상 비용</h2>
        <p>본문 {{ derived.baseCost.toLocaleString("ko-KR") }}원</p>
        <p v-if="derived.fallbackCost > 0">대체 발송 최대 {{ derived.fallbackCost.toLocaleString("ko-KR") }}원</p>
        <p>합계 {{ derived.totalCost.toLocaleString("ko-KR") }}원</p>
        <p>{{ balanceLabel }}</p>
        <p v-if="derived.blockReason" class="danger">{{ derived.blockReason }}</p>
        <p v-if="sendError" class="danger">{{ sendError }}</p>
      </section>
      <button type="submit" data-testid="send-button" :disabled="derived.blockReason !== null || sending">발송하기</button>
    </div>
    <PhonePreview :text="derived.preview" caption="받는 사람 화면" />
  </form>
</template>
