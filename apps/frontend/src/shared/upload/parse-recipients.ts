import { normalizePhone } from "@/shared/message-rules";
import * as XLSX from "xlsx";

export type RecipientRow = {
  raw: string;
  phone: string | null;
  variables: Record<string, string>;
  reason: string;
};

export type ClassifyResult = {
  valid: RecipientRow[];
  invalid: RecipientRow[];
  duplicate: RecipientRow[];
  optedOut: RecipientRow[];
  variables: Record<string, string[]>;
};

const phoneHeaders = new Set(["전화번호", "휴대폰", "휴대폰번호", "수신번호", "phone", "mobile", "번호"]);

const headerKey = (value: string) => value.trim().toLowerCase().replace(/\s+/g, "");

const textFromBytes = (data: ArrayBuffer | Uint8Array) => {
  const bytes = data instanceof Uint8Array ? data : new Uint8Array(data);
  const zip = bytes[0] === 0x50 && bytes[1] === 0x4b;
  const ole = bytes[0] === 0xd0 && bytes[1] === 0xcf;
  if (zip || ole) return null;
  try {
    return new TextDecoder("utf-8", { fatal: true }).decode(bytes);
  } catch {
    return null;
  }
};

export const readTable = (data: ArrayBuffer | Uint8Array | string) => {
  const text = typeof data === "string" ? data : textFromBytes(data);
  const source = text ?? (data instanceof Uint8Array ? data : new Uint8Array(data as ArrayBuffer));
  const workbook = XLSX.read(source, { type: typeof source === "string" ? "string" : "array" });
  const name = workbook.SheetNames[0];
  if (!name) return [];
  const rows = XLSX.utils.sheet_to_json<(string | number | boolean | null)[]>(workbook.Sheets[name], {
    header: 1,
    raw: false,
    defval: "",
    blankrows: false,
  });
  return rows.map((row) => (row ?? []).map((cell) => String(cell ?? "").trim()));
};

export const classifyRows = (table: string[][], optOuts: readonly string[]): ClassifyResult => {
  const opted = new Set(optOuts.flatMap((item) => {
    const parsed = normalizePhone(item);
    return parsed.normalized ? [parsed.normalized] : [];
  }));
  const headerRow = table[0] ?? [];
  const headerPhone = headerRow.findIndex((cell) => phoneHeaders.has(headerKey(cell)));
  const hasHeader = headerPhone >= 0;
  const data = hasHeader ? table.slice(1) : table;
  const phoneColumn = hasHeader ? headerPhone : 0;
  const headers = hasHeader
    ? headerRow
    : (data[0] ?? []).map((_, index) => (index === 0 ? "전화번호" : index === 1 ? "이름" : `열${index + 1}`));
  const result: ClassifyResult = { valid: [], invalid: [], duplicate: [], optedOut: [], variables: {} };
  const seen = new Set<string>();
  for (const row of data) {
    if (row.every((cell) => cell === "")) continue;
    const variables: Record<string, string> = {};
    headers.forEach((header, index) => {
      if (index === phoneColumn || header.trim() === "") return;
      variables[header.trim()] = row[index] ?? "";
    });
    const raw = row.filter((cell) => cell !== "").join(" ");
    const parsed = normalizePhone(row[phoneColumn] ?? "");
    const item = (reason: string, phone: string | null): RecipientRow => ({ raw, phone, variables, reason });
    if (!parsed.valid || !parsed.normalized) {
      result.invalid.push(item("번호 형식이 아니에요", null));
      continue;
    }
    if (opted.has(parsed.normalized)) {
      result.optedOut.push(item("수신거부 번호예요", parsed.normalized));
      continue;
    }
    if (seen.has(parsed.normalized)) {
      result.duplicate.push(item("같은 번호가 앞에 있어요", parsed.normalized));
      continue;
    }
    seen.add(parsed.normalized);
    result.valid.push(item("", parsed.normalized));
    for (const [key, value] of Object.entries(variables)) {
      if (!value) continue;
      const bucket = result.variables[key] ?? [];
      bucket.push(value);
      result.variables[key] = bucket;
    }
  }
  return result;
};

export const parseAndClassify = (data: ArrayBuffer | Uint8Array | string, optOuts: readonly string[]) =>
  classifyRows(readTable(data), optOuts);

export const invalidWorkbook = (rows: RecipientRow[]) => {
  const sheet = XLSX.utils.json_to_sheet(rows.map((row) => ({
    원본: row.raw,
    사유: row.reason,
    ...row.variables,
  })));
  const book = XLSX.utils.book_new();
  XLSX.utils.book_append_sheet(book, sheet, "오류");
  return XLSX.write(book, { type: "array", bookType: "xlsx" }) as Uint8Array;
};
