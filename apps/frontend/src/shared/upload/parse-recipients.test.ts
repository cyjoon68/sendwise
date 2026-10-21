import { describe, expect, it } from "vitest";
import * as XLSX from "xlsx";
import { classifyRows, parseAndClassify } from "./parse-recipients";

const optOuts = ["010-0000-0000", "01011112222"];

describe("classifyRows", () => {
  it("splits valid, invalid, duplicate, and opted-out rows", () => {
    const table = [
      ["전화번호", "이름"],
      ["01012345678", "김하늘"],
      ["010-1234-5678", "김하늘"],
      ["01099990000", "박서준"],
      ["+82 10 5555 6666", "이도윤"],
      ["abc", "오류"],
      ["01000000000", "거부"],
      ["01112345678", "구번호"],
      ["", ""],
    ];
    const result = classifyRows(table, optOuts);
    expect(result.valid.map((row) => row.phone)).toEqual(["01012345678", "01099990000", "01055556666"]);
    expect(result.duplicate.map((row) => row.phone)).toEqual(["01012345678"]);
    expect(result.optedOut.map((row) => row.phone)).toEqual(["01000000000"]);
    expect(result.invalid.map((row) => row.raw)).toEqual(["abc 오류", "01112345678 구번호"]);
    expect(result.variables.이름).toEqual(["김하늘", "박서준", "이도윤"]);
  });

  it("reads a utf-8 csv buffer with a korean header", () => {
    const csv = "전화번호,이름\n01012345678,김하늘\n";
    const result = parseAndClassify(new TextEncoder().encode(csv), []);
    expect(result.invalid).toHaveLength(0);
    expect(result.valid[0]?.phone).toBe("01012345678");
    expect(result.valid[0]?.variables.이름).toBe("김하늘");
  });

  it("reads an xlsx buffer", () => {
    const sheet = XLSX.utils.aoa_to_sheet([
      ["전화번호", "이름"],
      ["01012345678", "김하늘"],
    ]);
    const book = XLSX.utils.book_new();
    XLSX.utils.book_append_sheet(book, sheet, "수신자");
    const bytes = XLSX.write(book, { type: "array", bookType: "xlsx" }) as Uint8Array;
    const result = parseAndClassify(bytes, []);
    expect(result.valid.map((row) => row.phone)).toEqual(["01012345678"]);
    expect(result.valid[0]?.variables.이름).toBe("김하늘");
  });

  it("reads a headerless csv", () => {
    const result = parseAndClassify("01012345678,김하늘\n01012345678,김하늘", optOuts);
    expect(result.valid).toHaveLength(1);
    expect(result.duplicate).toHaveLength(1);
    expect(result.valid[0]?.variables.이름).toBe("김하늘");
  });
});
