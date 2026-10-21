import { describe, expect, it } from "vitest";
import { api, ApiError, setMockBalance } from "./index";

const recipients = (count: number) => Array.from({ length: count }, (_, index) => ({
  phone: `010${String(10_000_000 + index).slice(-8)}`,
  variables: { name: "홍길동" },
}));

const create = (count: number) => api.createCampaign({
  clientId: 1,
  channel: "SMS",
  purpose: "NORMAL",
  body: "안내",
  fallback: "NONE",
  fallbackBody: null,
  senderNumber: "0212345678",
  templateCode: null,
  companyName: null,
  scheduledAt: null,
  recipients: recipients(count),
});

describe("mock backend", () => {
  it("pages failed messages from zero and keeps recipient variables", async () => {
    const created = await create(55);
    const first = await api.messages(created.id, 0);
    const second = await api.messages(created.id, 1);
    expect(created.messageCount).toBe(55);
    expect(created.balanceAfter).toBe(1_000_000 - 55 * 9);
    expect(created.skippedOptOut).toEqual([]);
    expect(first.total).toBe(11);
    expect(first.size).toBe(10);
    expect(first.page).toBe(0);
    expect(first.items).toHaveLength(10);
    expect(second.items).toHaveLength(1);
    expect(second.page).toBe(1);
  });

  it("rejects a campaign that costs more than the balance", async () => {
    setMockBalance(0);
    await expect(create(1)).rejects.toMatchObject({ status: 402, error: "INSUFFICIENT_BALANCE" });
    await expect(create(1)).rejects.toBeInstanceOf(ApiError);
  });
});
