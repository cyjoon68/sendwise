import { messageRules } from "./rule-data";

const parseClock = (value: string) => {
  const [hour, minute] = value.split(":").map(Number);
  return hour * 60 + minute;
};

const clockMinutes = (iso: string) => {
  const parts = new Intl.DateTimeFormat("en-US", {
    timeZone: messageRules.ad.blockedHours.zone,
    hour: "2-digit",
    minute: "2-digit",
    hourCycle: "h23",
  }).formatToParts(new Date(iso));
  const pick = (type: Intl.DateTimeFormatPartTypes) => Number(parts.find((part) => part.type === type)?.value);
  return pick("hour") * 60 + pick("minute");
};

export const inBlockedHours = (iso: string) => {
  const minutes = clockMinutes(iso);
  const from = parseClock(messageRules.ad.blockedHours.from);
  const to = parseClock(messageRules.ad.blockedHours.to);
  const afterFrom = messageRules.ad.blockedHours.fromInclusive ? minutes >= from : minutes > from;
  const beforeTo = messageRules.ad.blockedHours.toInclusive ? minutes <= to : minutes < to;
  if (from > to) return afterFrom || beforeTo;
  return afterFrom && beforeTo;
};

export const adDecision = (input: {
  purpose: string;
  scheduledAt: string;
  body: string;
  companyName: string;
}) => {
  const blocked = inBlockedHours(input.scheduledAt);
  if (input.purpose !== "AD") return { inBlockedHours: blocked, allowed: true };
  const hasPrefix = input.body.startsWith(messageRules.ad.requiredPrefix);
  const hasOptOut = input.body.includes(messageRules.ad.optOutText);
  const hasCompanyName = !messageRules.ad.companyNameRequired
    || (input.companyName.length > 0 && input.body.includes(input.companyName));
  return {
    inBlockedHours: blocked,
    hasPrefix,
    hasOptOut,
    hasCompanyName,
    allowed: !blocked && hasPrefix && hasOptOut && hasCompanyName,
  };
};

export const joinAdBody = (body: string, companyName: string) => {
  const prefix = `${messageRules.ad.requiredPrefix}${companyName.trim()}`;
  return `${prefix}\n${body}\n${messageRules.ad.optOutText}`;
};
