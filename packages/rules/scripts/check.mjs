import { readFileSync, readdirSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";

const root = join(dirname(fileURLToPath(import.meta.url)), "..");
const COMMON = ["SUCCESS", "INVALID_NUMBER", "OPTED_OUT", "DEVICE_OFF", "SPAM_BLOCKED", "ETC"];

const readJson = (name) => JSON.parse(readFileSync(join(root, name), "utf8"));

const rules = readJson("message-rules.json");
const ks = readJson("ksx1001.json");
const carriers = readJson("carrier-codes.json");

const hangulSet = new Set(ks.hangul);
const nonSet = new Set(ks.nonHangul);

const clockMinutes = (iso) => {
  const parts = new Intl.DateTimeFormat("en-US", {
    timeZone: rules.ad.blockedHours.zone,
    hour: "2-digit",
    minute: "2-digit",
    hourCycle: "h23",
  }).formatToParts(new Date(iso));
  const pick = (type) => Number(parts.find((part) => part.type === type).value);
  return pick("hour") * 60 + pick("minute");
};

const parseClock = (value) => {
  const [hour, minute] = value.split(":").map(Number);
  return hour * 60 + minute;
};

export const inBlockedHours = (iso) => {
  const minutes = clockMinutes(iso);
  const from = parseClock(rules.ad.blockedHours.from);
  const to = parseClock(rules.ad.blockedHours.to);
  const afterFrom = rules.ad.blockedHours.fromInclusive ? minutes >= from : minutes > from;
  const beforeTo = rules.ad.blockedHours.toInclusive ? minutes <= to : minutes < to;
  if (from > to) return afterFrom || beforeTo;
  return afterFrom && beforeTo;
};

export const measure = (text) => {
  let bytes = 0;
  const unencodable = [];
  let inHangul2350 = text.length > 0;
  for (const ch of text) {
    const cp = ch.codePointAt(0);
    if (cp <= 0x7f) {
      bytes += rules.encoding.asciiBytes;
      inHangul2350 = false;
      continue;
    }
    if (hangulSet.has(ch)) {
      bytes += rules.encoding.ksx1001Bytes;
      continue;
    }
    inHangul2350 = false;
    if (nonSet.has(ch)) {
      bytes += rules.encoding.ksx1001Bytes;
      continue;
    }
    unencodable.push(ch);
  }
  if (unencodable.length > 0) {
    return { encodable: false, bytes: null, channel: null, overLimit: false, inHangul2350: false, unencodable };
  }
  const overLimit = bytes > rules.lms.maxBytes;
  let channel = null;
  if (!overLimit) channel = bytes <= rules.sms.maxBytes ? "SMS" : "LMS";
  return { encodable: true, bytes, channel, overLimit, inHangul2350, unencodable };
};

export const adDecision = (input) => {
  const blocked = inBlockedHours(input.scheduledAt);
  if (input.purpose !== "AD") return { inBlockedHours: blocked, allowed: true };
  const hasPrefix = input.body.startsWith(rules.ad.requiredPrefix);
  const hasOptOut = input.body.includes(rules.ad.optOutText);
  const hasCompanyName = !rules.ad.companyNameRequired
    || (typeof input.companyName === "string" && input.companyName.length > 0 && input.body.includes(input.companyName));
  return {
    inBlockedHours: blocked,
    hasPrefix,
    hasOptOut,
    hasCompanyName,
    allowed: !blocked && hasPrefix && hasOptOut && hasCompanyName,
  };
};

export const normalizePhone = (raw) => {
  let compact = raw.trim().replace(/[\s-]/g, "");
  if (compact.startsWith("+82")) compact = `0${compact.slice(3)}`;
  const valid = /^010\d{8}$/.test(compact);
  return { normalized: valid ? compact : null, valid };
};

export const mapCarrier = (input) => ({
  common: carriers[input.carrier]?.[input.code] ?? null,
});

export const personalize = (input) => {
  const worst = {};
  const shortest = {};
  for (const [name, values] of Object.entries(input.variables)) {
    let longest = values[0];
    let shortestValue = values[0];
    for (const value of values) {
      if (measure(value).bytes > measure(longest).bytes) longest = value;
      if (measure(value).bytes < measure(shortestValue).bytes) shortestValue = value;
    }
    worst[name] = longest;
    shortest[name] = shortestValue;
  }
  let rendered = input.template;
  let shortestRendered = input.template;
  for (const [name, value] of Object.entries(worst)) {
    rendered = rendered.split(`#{${name}}`).join(value);
    shortestRendered = shortestRendered.split(`#{${name}}`).join(shortest[name]);
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

const same = (left, right) => JSON.stringify(left) === JSON.stringify(right);

const checkShape = (errors) => {
  if (rules.sms.maxBytes !== 90) errors.push("sms.maxBytes");
  if (rules.lms.maxBytes !== 2000) errors.push("lms.maxBytes");
  if (rules.ad.blockedHours.from !== "21:00" || rules.ad.blockedHours.to !== "08:00") errors.push("blocked hours");
  if (rules.ad.blockedHours.zone !== "Asia/Seoul") errors.push("zone");
  if (rules.ad.requiredPrefix !== "(광고)") errors.push("prefix");
  if (rules.ad.optOutText !== "무료수신거부 080-000-0000") errors.push("opt-out");
  if (rules.unitPrice.SMS !== 9 || rules.unitPrice.LMS !== 30 || rules.unitPrice.ALIMTALK !== 7) errors.push("unit price");
  if (rules.encoding.asciiBytes !== 1 || rules.encoding.ksx1001Bytes !== 2) errors.push("byte widths");

  if ([...ks.hangul].length !== 2350 || ks.hangulCount !== 2350) errors.push(`hangul count ${[...ks.hangul].length}`);
  if (hangulSet.size !== 2350) errors.push("hangul unique");
  if (!ks.hangul.startsWith("가각간") || !ks.hangul.endsWith("힝")) errors.push("hangul order");
  if (ks.hangul.includes("똠") || ks.hangul.includes("햏")) errors.push("outside syllable in hangul");
  if ([...ks.nonHangul].length !== ks.nonHangulCount) errors.push("nonHangul count");
  for (const ch of ks.nonHangul) {
    if (hangulSet.has(ch)) {
      errors.push("overlap");
      break;
    }
  }
  if (ks.nonHangul.includes("€") || ks.nonHangul.includes("ㅤ") || ks.nonHangul.includes("\uE000")) {
    errors.push("disagreement leaked into nonHangul");
  }
  if (ks.codecDisagreements.length !== ks.codecDisagreementCount) errors.push("disagreement count");
  const traps = {
    A2E6: { python: "U+20AC", node: null },
    A2E7: { python: "U+00AE", node: null },
    A4D4: { python: null, node: "U+3164" },
  };
  for (const row of ks.codecDisagreements) {
    const n = Number.parseInt(row.bytes, 16);
    if (n >= 0xb0a1 && n <= 0xc8fe) errors.push(`disagreement in hangul rows ${row.bytes}`);
    const trap = traps[row.bytes];
    if (trap && (row.python !== trap.python || row.node !== trap.node)) errors.push(`trap ${row.bytes}`);
    delete traps[row.bytes];
  }
  for (const bytes of Object.keys(traps)) errors.push(`missing trap ${bytes}`);

  if (carriers.mock !== true) errors.push("carrier mock flag");
  for (const name of ["SKT", "KT", "LGU"]) {
    const table = carriers[name];
    const values = Object.values(table);
    for (const code of COMMON) {
      if (!values.includes(code)) errors.push(`${name} missing ${code}`);
    }
    for (const value of values) {
      if (!COMMON.includes(value)) errors.push(`${name} unknown ${value}`);
    }
  }
  for (const code of Object.keys(carriers.SKT)) {
    if (!/^\d+$/.test(code)) errors.push(`SKT code ${code}`);
  }
  for (const code of Object.keys(carriers.KT)) {
    if (!/^[A-Z]+[0-9]*$/.test(code)) errors.push(`KT code ${code}`);
  }
  for (const code of Object.keys(carriers.LGU)) {
    if (!/^[A-Z]$/.test(code)) errors.push(`LGU code ${code}`);
  }

  const clocks = [
    ["2026-10-22T20:59:00+09:00", false],
    ["2026-10-22T21:00:00+09:00", true],
    ["2026-10-23T07:59:00+09:00", true],
    ["2026-10-23T08:00:00+09:00", false],
    ["2026-10-22T12:00:00Z", true],
    ["2026-10-22T11:59:00Z", false],
  ];
  for (const [iso, blocked] of clocks) {
    if (inBlockedHours(iso) !== blocked) errors.push(`clock ${iso}`);
  }
};

const solvers = {
  "bytes.json": (input) => {
    const sized = measure(input.text);
    return {
      bytes: sized.bytes,
      channel: sized.channel,
      overLimit: sized.overLimit,
      encodable: sized.encodable,
    };
  },
  "encoding.json": (input) => {
    const sized = measure(input.text);
    return {
      encodable: sized.encodable,
      bytes: sized.bytes,
      inHangul2350: sized.inHangul2350,
      unencodable: sized.unencodable,
    };
  },
  "personalization.json": (input) => personalize(input),
  "ad.json": (input) => adDecision(input),
  "phone.json": (input) => normalizePhone(input.phone),
  "carrier.json": (input) => mapCarrier(input),
};

const checkCases = (errors) => {
  const dir = join(root, "cases");
  const files = readdirSync(dir).filter((name) => name.endsWith(".json")).sort();
  const known = Object.keys(solvers).sort();
  if (same(files, known) === false) errors.push(`case files ${files.join(",")}`);
  const names = new Set();
  let rows = 0;
  for (const file of files) {
    const body = JSON.parse(readFileSync(join(dir, file), "utf8"));
    if (!Array.isArray(body) || body.length === 0) errors.push(`${file} empty`);
    for (const row of body) {
      rows += 1;
      if (typeof row.name !== "string" || row.name.length === 0) errors.push(`${file} name`);
      if (names.has(row.name)) errors.push(`duplicate ${row.name}`);
      names.add(row.name);
      if (row.input == null || typeof row.input !== "object" || Array.isArray(row.input)) errors.push(`${row.name} input`);
      if (row.expected == null || typeof row.expected !== "object" || Array.isArray(row.expected)) errors.push(`${row.name} expected`);
      const computed = solvers[file](row.input);
      for (const [key, value] of Object.entries(row.expected)) {
        if (!same(computed[key], value)) {
          errors.push(`${file} ${row.name} ${key}: got ${JSON.stringify(computed[key])}`);
        }
      }
    }
  }
  return rows;
};

const main = () => {
  const errors = [];
  checkShape(errors);
  const caseRows = checkCases(errors);
  if (errors.length > 0) {
    for (const error of errors) process.stderr.write(`${error}\n`);
    process.exit(1);
  }
  process.stdout.write(
    `ok hangul=${ks.hangulCount} nonHangul=${ks.nonHangulCount} disagreements=${ks.codecDisagreementCount} cases=${caseRows}\n`,
  );
};

if (import.meta.url === pathToFileURL(process.argv[1]).href) main();
