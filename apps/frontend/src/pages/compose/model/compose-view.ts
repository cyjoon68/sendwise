import { formatWon } from "@/shared/lib";
import {
  adDecision,
  joinAdBody,
  messageRules,
  personalize,
  splitByCharset,
  suggestReplacement,
  type TextChannel,
} from "@/shared/message-rules";
import type { FallbackMode, Purpose } from "@/shared/api";
import { matchTemplate } from "./template-match";

export type ComposeDraft = {
  mode: "TEXT" | "ALIMTALK";
  purpose: Purpose;
  body: string;
  companyName: string;
  scheduledAt: string;
  senderNumber: string;
  templateBody: string;
  fallback: FallbackMode;
  fallbackBody: string;
  recipientCount: number;
  variableValues: Record<string, string[]>;
  previewVariables: Record<string, string>;
  balance: number | null;
  nowIso: string;
};

const filled = (text: string, values: Record<string, string>) => text.replace(
  /#\{([^}]+)\}/g,
  (token, name: string) => (Object.prototype.hasOwnProperty.call(values, name) ? values[name] ?? "" : token),
);

const suggestionsOf = (chars: string[]) => [...new Set(chars)].map((char) => ({
  char,
  suggestion: suggestReplacement(char),
}));

const meterText = (bytes: number | null) => {
  if (bytes === null) return "바이트를 계산할 수 없어요";
  const limit = bytes <= messageRules.sms.maxBytes ? messageRules.sms.maxBytes : messageRules.lms.maxBytes;
  return `${bytes.toLocaleString("ko-KR")} / ${limit.toLocaleString("ko-KR")}바이트`;
};

const priceNote = (channel: TextChannel | null, overLimit: boolean, encodable: boolean) => {
  const delta = messageRules.unitPrice.LMS - messageRules.unitPrice.SMS;
  if (!encodable) return "깨지는 글자가 있어요. 바꾸기 전에는 길이를 확정할 수 없어요.";
  if (overLimit) return `${messageRules.lms.maxBytes.toLocaleString("ko-KR")}바이트를 넘어서 보낼 수 없어요.`;
  if (channel === "LMS") return `LMS예요. SMS보다 건당 ${delta.toLocaleString("ko-KR")}원 더 들어요.`;
  return `SMS예요. ${messageRules.sms.maxBytes}바이트를 넘으면 건당 ${delta.toLocaleString("ko-KR")}원 더 들어요.`;
};

export const scheduledIso = (value: string, nowIso: string) => {
  if (!value) return nowIso;
  if (value.endsWith("Z") || /[+-]\d{2}:\d{2}$/.test(value)) return value;
  const withSeconds = value.length === 16 ? `${value}:00` : value;
  return `${withSeconds}+09:00`;
};

const usableVariables = (values: Record<string, string[]>) => Object.fromEntries(
  Object.entries(values).filter(([, list]) => list.length > 0),
);

export const deriveCompose = (draft: ComposeDraft) => {
  const companyName = draft.companyName.trim();
  const adText = draft.purpose === "AD" && draft.mode === "TEXT";
  const fullBody = adText ? joinAdBody(draft.body, companyName) : draft.body;
  const variables = usableVariables(draft.variableValues);
  const sized = personalize({ template: fullBody, variables });
  const fallbackSource = draft.fallback === "CUSTOM" ? draft.fallbackBody : draft.body;
  const fallbackFull = adText ? joinAdBody(fallbackSource, companyName) : fallbackSource;
  const fallbackSized = personalize({ template: fallbackFull, variables });
  const when = scheduledIso(draft.scheduledAt, draft.nowIso);
  const decision = adDecision({
    purpose: draft.purpose,
    scheduledAt: when,
    body: fullBody,
    companyName,
  });
  const template = draft.templateBody
    ? matchTemplate(draft.templateBody, draft.body)
    : { ok: false as const, index: 0 };
  const textChannel = draft.mode === "TEXT" ? sized.channel : null;
  const sendChannel = draft.mode === "ALIMTALK" ? "ALIMTALK" : sized.channel;
  const fallbackActive = draft.mode === "ALIMTALK" && draft.fallback !== "NONE";
  const unit = sendChannel ? messageRules.unitPrice[sendChannel] : 0;
  const baseCost = unit * draft.recipientCount;
  const fallbackUnit = fallbackActive && fallbackSized.channel ? messageRules.unitPrice[fallbackSized.channel] : 0;
  const fallbackCost = fallbackUnit * draft.recipientCount;
  const totalCost = baseCost + fallbackCost;
  const shortfall = draft.balance === null ? 0 : Math.max(0, totalCost - draft.balance);
  const previewValues = Object.keys(draft.previewVariables).length > 0 ? draft.previewVariables : sized.worst;
  const blockReason = (() => {
    if (draft.mode === "ALIMTALK" && draft.purpose === "AD") return "광고는 문자로 보내 주세요";
    if (draft.mode === "TEXT" && !sized.encodable) return "깨지는 글자를 바꿔 주세요";
    if (draft.mode === "TEXT" && sized.overLimit) return "2,000바이트를 넘어서 보낼 수 없어요";
    if (draft.mode === "TEXT" && draft.body.trim() === "") return "본문을 입력해 주세요";
    if (draft.mode === "ALIMTALK" && draft.templateBody === "") return "템플릿을 선택해 주세요";
    if (draft.mode === "ALIMTALK" && !template.ok) return "템플릿과 다른 부분이 있어요";
    if (draft.mode === "ALIMTALK" && draft.fallback === "CUSTOM" && draft.fallbackBody.trim() === "") {
      return "대체 문자를 입력해 주세요";
    }
    if (fallbackActive && !fallbackSized.encodable) return "대체 문자에 깨지는 글자가 있어요";
    if (fallbackActive && fallbackSized.overLimit) return "대체 문자가 2,000바이트를 넘어요";
    if (draft.purpose === "AD" && companyName === "") return "업체명을 입력해 주세요";
    if (draft.purpose === "AD" && !decision.allowed) return "광고 문자는 21:00–08:00에는 보낼 수 없어요";
    if (draft.senderNumber === "") return "발신번호를 선택해 주세요";
    if (draft.recipientCount === 0) return "보낼 번호를 올려 주세요";
    if (shortfall > 0) return `${formatWon(shortfall)} 부족해요`;
    return null;
  })();
  return {
    fullBody,
    preview: filled(draft.mode === "ALIMTALK" ? draft.body : fullBody, previewValues),
    bytes: sized.bytes,
    channel: sendChannel,
    textChannel,
    overLimit: sized.overLimit,
    encodable: sized.encodable,
    meter: meterText(sized.bytes),
    priceNote: priceNote(sized.channel, sized.overLimit, sized.encodable),
    parts: splitByCharset(draft.body),
    suggestions: suggestionsOf(splitByCharset(draft.body).flatMap((part) => (part.outside ? [...part.text] : []))),
    fallbackBytes: fallbackSized.bytes,
    fallbackChannel: fallbackSized.channel,
    fallbackMeter: meterText(fallbackSized.bytes),
    fallbackNote: priceNote(fallbackSized.channel, fallbackSized.overLimit, fallbackSized.encodable),
    fallbackParts: splitByCharset(draft.fallbackBody),
    fallbackSuggestions: suggestionsOf(
      splitByCharset(draft.fallbackBody).flatMap((part) => (part.outside ? [...part.text] : [])),
    ),
    templateOk: template.ok,
    templateMismatchIndex: template.ok ? draft.body.length : template.index,
    blockedHours: decision.inBlockedHours,
    baseCost,
    fallbackCost,
    totalCost,
    shortfall,
    blockReason,
  };
};


