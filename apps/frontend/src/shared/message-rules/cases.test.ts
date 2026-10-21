import fs from "node:fs";
import path from "node:path";
import adCases from "@rules/cases/ad.json";
import bytesCases from "@rules/cases/bytes.json";
import carrierCases from "@rules/cases/carrier.json";
import encodingCases from "@rules/cases/encoding.json";
import personalizationCases from "@rules/cases/personalization.json";
import phoneCases from "@rules/cases/phone.json";
import { describe, expect, it } from "vitest";
import { adDecision } from "./ad-decision";
import { mapCarrier } from "./carrier-code";
import { measure, personalize, suggestReplacement } from "./measure-text";
import { normalizePhone } from "./phone-number";
import { hangulSet, nonHangulSet } from "./rule-data";

type RuleCase = { name: string; input: Record<string, unknown>; expected: Record<string, unknown> };

const solvers: Record<string, (input: RuleCase["input"]) => Record<string, unknown>> = {
  "bytes.json": (input) => {
    const sized = measure(input.text as string);
    return {
      bytes: sized.bytes,
      channel: sized.channel,
      overLimit: sized.overLimit,
      encodable: sized.encodable,
    };
  },
  "encoding.json": (input) => {
    const sized = measure(input.text as string);
    return {
      encodable: sized.encodable,
      bytes: sized.bytes,
      inHangul2350: sized.inHangul2350,
      unencodable: sized.unencodable,
    };
  },
  "personalization.json": (input) => personalize(input as never),
  "ad.json": (input) => adDecision(input as never),
  "phone.json": (input) => normalizePhone((input as { phone: string }).phone),
  "carrier.json": (input) => mapCarrier(input as { carrier: string; code: string }),
};

const tables: Record<string, RuleCase[]> = {
  "bytes.json": bytesCases as RuleCase[],
  "encoding.json": encodingCases as RuleCase[],
  "personalization.json": personalizationCases as RuleCase[],
  "ad.json": adCases as RuleCase[],
  "phone.json": phoneCases as RuleCase[],
  "carrier.json": carrierCases as RuleCase[],
};

describe("packages/rules cases", () => {
  it("covers every case file", () => {
    const dir = path.resolve(process.cwd(), "../../packages/rules/cases");
    const files = fs.readdirSync(dir).filter((name) => name.endsWith(".json")).sort();
    expect(files).toEqual(Object.keys(tables).sort());
  });

  for (const [file, rows] of Object.entries(tables)) {
    for (const row of rows) {
      it(`${file} ${row.name}`, () => {
        const actual = solvers[file](row.input);
        for (const [key, value] of Object.entries(row.expected)) {
          expect(actual[key]).toEqual(value);
        }
      });
    }
  }

  it("counts fullwidth won as two bytes and rejects the other won sign", () => {
    expect(nonHangulSet.has("\uFFE6")).toBe(true);
    expect(measure("\uFFE6")).toMatchObject({ encodable: true, bytes: 2, channel: "SMS" });
    expect(measure("\u20A9").encodable).toBe(false);
    expect(hangulSet.has("똠")).toBe(false);
    expect(suggestReplacement("똠")).toBe("또");
    expect(suggestReplacement("😀")).toBe("?");
  });
});
