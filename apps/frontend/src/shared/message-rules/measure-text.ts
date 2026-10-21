import { hangulSet, messageRules, nonHangulSet } from "./rule-data";

export type TextMeasure = {
  encodable: boolean;
  bytes: number | null;
  channel: "SMS" | "LMS" | null;
  overLimit: boolean;
  inHangul2350: boolean;
  unencodable: string[];
};

const rank = (bytes: number | null) => bytes ?? 0;

export const measure = (text: string): TextMeasure => {
  let bytes = 0;
  const unencodable: string[] = [];
  let inHangul2350 = text.length > 0;
  for (const char of text) {
    const codePoint = char.codePointAt(0) ?? 0;
    if (codePoint <= 0x7f) {
      bytes += messageRules.encoding.asciiBytes;
      inHangul2350 = false;
      continue;
    }
    if (hangulSet.has(char)) {
      bytes += messageRules.encoding.ksx1001Bytes;
      continue;
    }
    inHangul2350 = false;
    if (nonHangulSet.has(char)) {
      bytes += messageRules.encoding.ksx1001Bytes;
      continue;
    }
    unencodable.push(char);
  }
  if (unencodable.length > 0) {
    return {
      encodable: false,
      bytes: null,
      channel: null,
      overLimit: false,
      inHangul2350: false,
      unencodable,
    };
  }
  const overLimit = bytes > messageRules.lms.maxBytes;
  const channel = overLimit ? null : bytes <= messageRules.sms.maxBytes ? "SMS" : "LMS";
  return { encodable: true, bytes, channel, overLimit, inHangul2350, unencodable };
};

export const personalize = (input: { template: string; variables: Record<string, string[]> }) => {
  const worst: Record<string, string> = {};
  const shortest: Record<string, string> = {};
  for (const [name, values] of Object.entries(input.variables)) {
    let longest = values[0] ?? "";
    let shortestValue = values[0] ?? "";
    for (const value of values) {
      if (rank(measure(value).bytes) > rank(measure(longest).bytes)) longest = value;
      if (rank(measure(value).bytes) < rank(measure(shortestValue).bytes)) shortestValue = value;
    }
    worst[name] = longest;
    shortest[name] = shortestValue;
  }
  let rendered = input.template;
  let shortestRendered = input.template;
  for (const [name, value] of Object.entries(worst)) {
    rendered = rendered.split(`#{${name}}`).join(value);
    shortestRendered = shortestRendered.split(`#{${name}}`).join(shortest[name] ?? "");
  }
  const sized = measure(rendered);
  const shortSized = measure(shortestRendered);
  return {
    worst,
    rendered,
    bytes: sized.bytes,
    channel: sized.channel,
    overLimit: sized.overLimit,
    encodable: sized.encodable,
    shortestBytes: shortSized.bytes,
    shortestChannel: shortSized.channel,
  };
};

export const suggestReplacement = (char: string) => {
  const codePoint = char.codePointAt(0) ?? 0;
  const hangulBase = 0xac00;
  const jongCount = 28;
  if (codePoint >= hangulBase && codePoint <= 0xd7a3) {
    const jong = (codePoint - hangulBase) % jongCount;
    if (jong !== 0) {
      const withoutJong = String.fromCodePoint(codePoint - jong);
      if (hangulSet.has(withoutJong) || nonHangulSet.has(withoutJong)) return withoutJong;
    }
  }
  return "?";
};

export const splitByCharset = (text: string) => {
  const pending = [...measure(text).unencodable];
  const parts: { text: string; outside: boolean }[] = [];
  let current: { text: string; outside: boolean } | null = null;
  for (const char of text) {
    const outside = pending[0] === char;
    if (outside) pending.shift();
    if (!current || current.outside !== outside) {
      current = { text: char, outside };
      parts.push(current);
      continue;
    }
    current.text += char;
  }
  return parts;
};
