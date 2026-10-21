import carrierCodesJson from "@rules/carrier-codes.json";
import ksx1001Json from "@rules/ksx1001.json";
import messageRulesJson from "@rules/message-rules.json";

export const messageRules = messageRulesJson;
export const ksx1001 = ksx1001Json;
export const carrierCodes = carrierCodesJson;

export const hangulSet = new Set([...ksx1001.hangul]);
export const nonHangulSet = new Set([...ksx1001.nonHangul]);

export type MessageRules = typeof messageRules;
export type TextChannel = "SMS" | "LMS";
export type Channel = TextChannel | "ALIMTALK";
