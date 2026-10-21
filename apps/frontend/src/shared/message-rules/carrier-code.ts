import { carrierCodes } from "./rule-data";

const COMMON_CODES = ["SUCCESS", "INVALID_NUMBER", "OPTED_OUT", "DEVICE_OFF", "SPAM_BLOCKED", "ETC"] as const;

export type CommonCode = (typeof COMMON_CODES)[number];

export const commonCodeCopy: Record<CommonCode, { label: string; explanation: string; action: string }> = {
  SUCCESS: { label: "성공", explanation: "상대가 메시지를 받았어요", action: "따로 할 일은 없어요" },
  INVALID_NUMBER: { label: "결번", explanation: "없는 번호라 전달하지 못했어요", action: "번호를 확인한 뒤 다시 등록해 주세요" },
  OPTED_OUT: { label: "수신거부", explanation: "수신을 거부한 번호예요", action: "수신거부 목록을 확인해 주세요" },
  DEVICE_OFF: { label: "단말 꺼짐", explanation: "휴대폰이 꺼져 있어 전달하지 못했어요", action: "잠시 후 다시 보내 주세요" },
  SPAM_BLOCKED: { label: "스팸 차단", explanation: "스팸으로 차단됐어요", action: "문구를 바꾼 뒤 다시 보내 주세요" },
  ETC: { label: "기타", explanation: "다른 이유로 전달하지 못했어요", action: "같은 오류가 반복되면 문의해 주세요" },
};

export const mapCarrier = (input: { carrier: string; code: string }) => {
  const table = carrierCodes[input.carrier as keyof typeof carrierCodes];
  if (typeof table !== "object" || table === null) return { common: null };
  const common = table[input.code as keyof typeof table];
  return { common: typeof common === "string" ? common : null };
};

export const carrierCodeFor = (carrier: "SKT" | "KT" | "LGU", common: CommonCode) => {
  const table = carrierCodes[carrier];
  const found = Object.entries(table).find(([, value]) => value === common);
  return found?.[0] ?? "";
};

export const isCommonCode = (value: string): value is CommonCode =>
  (COMMON_CODES as readonly string[]).includes(value);
