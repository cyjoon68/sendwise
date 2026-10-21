import { describe, expect, it } from "vitest";
import { deriveCompose, type ComposeDraft } from "./compose-view";

const draft = (overrides: Partial<ComposeDraft>): ComposeDraft => ({
  mode: "TEXT",
  purpose: "NORMAL",
  body: "",
  companyName: "",
  scheduledAt: "",
  senderNumber: "0215880000",
  templateBody: "",
  fallback: "NONE",
  fallbackBody: "",
  recipientCount: 1,
  variableValues: {},
  previewVariables: {},
  balance: 50_000,
  nowIso: "2026-05-20T03:00:00.000Z",
  ...overrides,
});

describe("deriveCompose", () => {
  it("skips the shortfall while the balance is unknown", () => {
    const unknown = deriveCompose(draft({ body: "가", balance: null }));
    expect(unknown.shortfall).toBe(0);
    expect(unknown.blockReason).toBeNull();
    expect(deriveCompose(draft({ body: "가", balance: 0 })).blockReason).toBe("9원 부족해요");
  });

  it("marks characters outside EUC-KR", () => {
    const view = deriveCompose(draft({ body: "안녕😀" }));
    expect(view.encodable).toBe(false);
    expect(view.parts.filter((part) => part.outside).map((part) => part.text)).toEqual(["😀"]);
    expect(view.blockReason).toBe("깨지는 글자를 바꿔 주세요");
  });

  it("switches to LMS and shows the 21 won delta", () => {
    const view = deriveCompose(draft({ body: "가".repeat(46) }));
    expect(view.bytes).toBe(92);
    expect(view.textChannel).toBe("LMS");
    expect(view.priceNote).toBe("LMS예요. SMS보다 건당 21원 더 들어요.");
  });

  it("blocks send when the balance is short", () => {
    const view = deriveCompose(draft({ body: "가", balance: 0 }));
    expect(view.totalCost).toBe(9);
    expect(view.blockReason).toBe("9원 부족해요");
  });

  it("blocks ad sends from 21:00 and allows them at 08:00", () => {
    const base = draft({
      purpose: "AD",
      companyName: "센드와이즈",
      body: "오늘 안내",
    });
    expect(deriveCompose({ ...base, scheduledAt: "2026-05-20T21:00" }).blockReason).toBe(
      "광고 문자는 21:00–08:00에는 보낼 수 없어요",
    );
    expect(deriveCompose({ ...base, scheduledAt: "2026-05-20T08:00" }).blockReason).toBeNull();
  });

  it("adds fallback price and flags a template mismatch", () => {
    const template = "#{이름}님, 주문이 접수됐어요. 주문번호 #{주문번호}";
    const matched = deriveCompose(draft({
      mode: "ALIMTALK",
      body: template,
      templateBody: template,
      fallback: "SAME",
      recipientCount: 2,
    }));
    expect(matched.templateOk).toBe(true);
    expect(matched.fallbackChannel === "SMS" || matched.fallbackChannel === "LMS").toBe(true);
    const fallbackUnit = matched.fallbackChannel === "LMS" ? 30 : 9;
    expect(matched.totalCost).toBe(7 * 2 + fallbackUnit * 2);
    expect(matched.blockReason).toBeNull();

    const mismatched = deriveCompose(draft({
      mode: "ALIMTALK",
      body: "다른 문장",
      templateBody: template,
      fallback: "NONE",
    }));
    expect(mismatched.templateOk).toBe(false);
    expect(mismatched.blockReason).toBe("템플릿과 다른 부분이 있어요");
  });
});
