import { DEFAULT_CLIENT_ID, FAILED_PAGE_SIZE } from "@/shared/config";
import type { MessageRules } from "@/shared/message-rules";
import { mockFile, mockRequest } from "./mock-backend";
import { ApiError } from "./sendwise-contract";
import type {
  AlimtalkTemplate,
  CampaignCreateInput,
  CampaignCreateResponse,
  CampaignDetail,
  ExportJob,
  MessagePage,
  SenderNumber,
  StatsGroup,
  StatsResponse,
} from "./sendwise-contract";

let clientId = DEFAULT_CLIENT_ID;

const useMock = import.meta.env.VITE_USE_MOCK !== "false";
const base = import.meta.env.VITE_API_BASE || "http://localhost:8080";

export const setClientId = (value: number) => {
  clientId = value;
};

const withClient = (path: string) => {
  const join = path.includes("?") ? "&" : "?";
  return `${path}${join}clientId=${clientId}`;
};

const errorFrom = (status: number, payload: Record<string, unknown>, fallback: string) => {
  const error = typeof payload.error === "string" ? payload.error : "VALIDATION";
  const message = typeof payload.message === "string" ? payload.message : fallback;
  return new ApiError(status, error, message, {
    balance: typeof payload.balance === "number" ? payload.balance : undefined,
    cost: typeof payload.cost === "number" ? payload.cost : undefined,
    shortfall: typeof payload.shortfall === "number" ? payload.shortfall : undefined,
  });
};

const request = async <T>(method: string, path: string, body?: unknown): Promise<T> => {
  if (useMock) return mockRequest(method, path, body) as T;
  const response = await fetch(`${base}${path}`, {
    method,
    headers: body === undefined ? undefined : { "content-type": "application/json" },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  if (response.status === 204) return undefined as T;
  const payload = await response.json().catch(() => ({})) as Record<string, unknown>;
  if (!response.ok) throw errorFrom(response.status, payload, "요청이 실패했어요");
  return payload as T;
};

export const api = {
  setClientId,
  rules: () => request<MessageRules>("GET", "/v1/rules"),
  senderNumbers: () => request<SenderNumber[]>("GET", withClient("/v1/sender-numbers")),
  createSenderNumber: (phone: string) => request<SenderNumber>("POST", "/v1/sender-numbers", { clientId, phone }),
  reviewSenderNumber: (id: number) => request<SenderNumber>(
    "PATCH",
    `/v1/sender-numbers/${id}`,
    { status: "REVIEWING", rejectReason: null },
  ),
  deleteSenderNumber: (id: number) => request<null>("DELETE", `/v1/sender-numbers/${id}`),
  templates: () => request<AlimtalkTemplate[]>("GET", withClient("/v1/alimtalk-templates")),
  campaign: (id: number) => request<CampaignDetail>("GET", `/v1/campaigns/${id}`),
  messages: (id: number, page: number, size = FAILED_PAGE_SIZE) => request<MessagePage>(
    "GET",
    `/v1/campaigns/${id}/messages?status=FAILED&page=${page}&size=${size}`,
  ),
  createCampaign: (body: CampaignCreateInput) => request<CampaignCreateResponse>("POST", "/v1/campaigns", body),
  startExport: (id: number) => request<ExportJob>("POST", `/v1/campaigns/${id}/exports`),
  exportStatus: (id: number) => request<ExportJob>("GET", `/v1/exports/${id}`),
  exportFile: async (id: number) => {
    const path = `/v1/exports/${id}/file`;
    if (useMock) return new Uint8Array(mockFile(path));
    const response = await fetch(`${base}${path}`);
    if (!response.ok) {
      const payload = await response.json().catch(() => ({})) as Record<string, unknown>;
      throw errorFrom(response.status, payload, "파일을 받지 못했어요");
    }
    return new Uint8Array(await response.arrayBuffer());
  },
  stats: (query: { from: string; to: string; groupBy: StatsGroup }) => request<StatsResponse>(
    "GET",
    `/v1/stats?from=${query.from}&to=${query.to}&groupBy=${query.groupBy}`,
  ),
};
