import { adDecision, carrierCodeFor, commonCodeCopy, isCommonCode, measure, messageRules, normalizePhone } from "@/shared/message-rules";
import type { CommonCode } from "@/shared/message-rules";
import * as XLSX from "xlsx";
import { ApiError } from "./sendwise-contract";
import type {
  CampaignCreateInput,
  CampaignDetail,
  CampaignMessage,
  CarrierName,
  Channel,
  ExportJob,
  ExportStatus,
  MessageStatus,
  SenderNumber,
  StatsGroup,
  StatsResponse,
} from "./sendwise-contract";

type StoredMessage = {
  id: number;
  campaignId: number;
  recipient: string;
  carrier: CarrierName;
  channel: Channel;
  status: MessageStatus;
  carrierCode: string | null;
  commonCode: string | null;
  cost: number;
  createdAt: string;
  fallbackOfId: number | null;
};

type StoredExport = {
  id: number;
  campaignId: number;
  status: ExportStatus;
  error: string | null;
};

type Database = {
  balance: number;
  optOuts: string[];
  senders: SenderNumber[];
  templates: {
    id: number;
    clientId: number;
    templateCode: string;
    body: string;
    variables: string[];
    status: "APPROVED";
  }[];
  campaigns: CampaignDetail[];
  messages: StoredMessage[];
  exports: StoredExport[];
};

const failPlan: CommonCode[] = ["INVALID_NUMBER", "DEVICE_OFF", "SPAM_BLOCKED", "OPTED_OUT", "ETC"];
const files = new Map<number, Uint8Array>();
const exportHeaders = ["번호", "수신번호", "통신사", "채널", "상태", "통신사코드", "공통코드", "설명", "다음 조치", "대체발송 원본"];
let sequence = 1;

const nextId = () => {
  const id = sequence;
  sequence += 1;
  return id;
};

const copyOf = (common: string | null) => {
  if (common && isCommonCode(common)) {
    return { explanation: commonCodeCopy[common].explanation, action: commonCodeCopy[common].action };
  }
  return { explanation: "다른 이유로 전달하지 못했어요", action: "같은 오류가 반복되면 문의해 주세요" };
};

const carrierOf = (phone: string): CarrierName => {
  const last = Number(phone.at(-1));
  const mod = ((last % 3) + 3) % 3;
  if (mod === 0) return "SKT";
  if (mod === 1) return "KT";
  return "LGU";
};

const seoulDay = (iso: string) => new Intl.DateTimeFormat("en-CA", {
  timeZone: "Asia/Seoul",
  year: "numeric",
  month: "2-digit",
  day: "2-digit",
}).format(new Date(iso));

const compactSender = (raw: string) => {
  const compact = raw.trim().replace(/ /g, "").replace(/-/g, "");
  return /^0\d{7,10}$/.test(compact) ? compact : null;
};

const renderTemplate = (template: string, variables: Record<string, string>) => template.replace(
  /#\{([^}]+)\}/g,
  (_token, name: string) => variables[name] ?? "",
);

const variablesOf = (value: unknown) => {
  if (value === undefined || value === null) return {};
  if (typeof value !== "object" || Array.isArray(value)) throw new ApiError(400, "INVALID_VARIABLES", "변수는 객체로 보내 주세요");
  const variables: Record<string, string> = {};
  for (const [key, item] of Object.entries(value)) variables[key] = item == null ? "" : String(item);
  return variables;
};

const requireText = (text: string) => {
  const sized = measure(text);
  if (!sized.encodable) throw new ApiError(400, "UNENCODABLE", "완성형에 없는 문자가 있어요");
  if (sized.overLimit || sized.channel === null) throw new ApiError(400, "BODY_TOO_LONG", "본문이 2,000바이트를 넘어요");
  return sized;
};

const seed = (): Database => {
  sequence = 1;
  files.clear();
  const createdAt = new Date(Date.now() - 24 * 60 * 60 * 1000).toISOString();
  const senderId = nextId();
  const shippedId = nextId();
  const authId = nextId();
  const campaignId = nextId();
  const messages: StoredMessage[] = [
    {
      id: nextId(),
      campaignId,
      recipient: "01010000000",
      carrier: "SKT",
      channel: "SMS",
      status: "DELIVERED",
      carrierCode: carrierCodeFor("SKT", "SUCCESS"),
      commonCode: "SUCCESS",
      cost: messageRules.unitPrice.SMS,
      createdAt,
      fallbackOfId: null,
    },
    {
      id: nextId(),
      campaignId,
      recipient: "01010000002",
      carrier: "LGU",
      channel: "SMS",
      status: "FAILED",
      carrierCode: carrierCodeFor("LGU", "INVALID_NUMBER"),
      commonCode: "INVALID_NUMBER",
      cost: messageRules.unitPrice.SMS,
      createdAt,
      fallbackOfId: null,
    },
  ];
  return {
    balance: 1_000_000,
    optOuts: ["01099998888"],
    senders: [{ id: senderId, clientId: 1, phone: "0212345678", status: "APPROVED", rejectReason: null }],
    templates: [
      {
        id: shippedId,
        clientId: 1,
        templateCode: "ORDER_SHIPPED",
        body: "#{name}님, 주문 #{orderNo}가 출고되었습니다.",
        variables: ["name", "orderNo"],
        status: "APPROVED",
      },
      {
        id: authId,
        clientId: 1,
        templateCode: "AUTH_CODE",
        body: "인증번호는 #{code}입니다.",
        variables: ["code"],
        status: "APPROVED",
      },
    ],
    campaigns: [{
      id: campaignId,
      clientId: 1,
      channel: "SMS",
      purpose: "NORMAL",
      fallback: "NONE",
      senderNumber: "0212345678",
      templateCode: null,
      scheduledAt: null,
      estimatedCost: messages.length * messageRules.unitPrice.SMS,
      createdAt,
      counts: { QUEUED: 0, SENT: 0, DELIVERED: 1, FAILED: 1 },
      fallbackCount: 0,
    }],
    messages,
    exports: [],
  };
};

let db = seed();

const knownClient = (id: number) => {
  if (id !== 1) throw new ApiError(404, "CLIENT_NOT_FOUND", "고객사를 찾을 수 없어요");
};

const clientQuery = (url: URL) => {
  const raw = url.searchParams.get("clientId");
  if (raw === null || raw === "" || !Number.isInteger(Number(raw))) throw new ApiError(400, "CLIENT_REQUIRED", "고객사 번호가 필요해요");
  return Number(raw);
};

const requireCampaign = (id: number) => {
  const campaign = db.campaigns.find((item) => item.id === id);
  if (!campaign) throw new ApiError(404, "CAMPAIGN_NOT_FOUND", "캠페인을 찾을 수 없어요");
  return campaign;
};

const toMessage = (message: StoredMessage): CampaignMessage => {
  const explained = message.commonCode !== null || message.status === "FAILED";
  const copy = explained ? copyOf(message.commonCode) : null;
  return {
    id: message.id,
    recipient: message.recipient,
    carrier: message.carrier,
    channel: message.channel,
    status: message.status,
    carrierCode: message.carrierCode,
    commonCode: message.commonCode,
    explanation: copy?.explanation ?? null,
    actionHint: copy?.action ?? null,
    fallbackOfId: message.fallbackOfId,
    cost: message.cost,
  };
};

type Prepared = { phone: string; body: string; variables: Record<string, string>; fallbackUnit: number };

const prepareRecipients = (input: CampaignCreateInput) => {
  const rows = Array.isArray(input.recipients) ? input.recipients : [];
  if (rows.length === 0) throw new ApiError(400, "RECIPIENTS_REQUIRED", "수신번호가 필요해요");
  const prepared: Prepared[] = [];
  const skipped: string[] = [];
  const seen = new Set<string>();
  for (const row of rows) {
    const rawPhone = typeof row === "string" ? row : (row.phone ?? "");
    const phone = normalizePhone(rawPhone);
    if (!phone.valid || !phone.normalized) throw new ApiError(400, "INVALID_RECIPIENT", "수신번호 형식을 확인해 주세요");
    if (seen.has(phone.normalized)) continue;
    seen.add(phone.normalized);
    if (db.optOuts.includes(phone.normalized)) {
      skipped.push(phone.normalized);
      continue;
    }
    const variables = typeof row === "string" ? {} : variablesOf(row.variables);
    const text = renderTemplate(input.body, variables);
    if (text.trim() === "") throw new ApiError(400, "BODY_REQUIRED", "본문을 입력해 주세요");
    if (input.channel === "SMS" || input.channel === "LMS") {
      const sized = requireText(text);
      if (sized.channel !== input.channel) throw new ApiError(400, "CHANNEL_MISMATCH", `이 본문은 ${sized.channel}로 보내야 해요`);
    }
    let fallbackUnit = 0;
    if (input.fallback !== "NONE") {
      const fallbackText = input.fallback === "SAME" ? text : renderTemplate(input.fallbackBody ?? "", variables);
      if (fallbackText.trim() === "") throw new ApiError(400, "FALLBACK_BODY_REQUIRED", "대체 문구를 입력해 주세요");
      const sized = requireText(fallbackText);
      fallbackUnit = sized.channel ? messageRules.unitPrice[sized.channel] : 0;
    }
    prepared.push({ phone: phone.normalized, body: text, variables, fallbackUnit });
  }
  if (prepared.length === 0) throw new ApiError(400, "NO_RECIPIENTS", "보낼 수 있는 수신번호가 없어요");
  return { prepared, skipped };
};

const createCampaign = (input: CampaignCreateInput) => {
  if (input.clientId == null) throw new ApiError(400, "CLIENT_REQUIRED", "고객사 번호가 필요해요");
  knownClient(input.clientId);
  if (!input.body || input.body.trim() === "") throw new ApiError(400, "BODY_REQUIRED", "본문을 입력해 주세요");
  if (input.channel !== "SMS" && input.channel !== "LMS" && input.channel !== "ALIMTALK") {
    throw new ApiError(400, "INVALID_CHANNEL", "요청 값을 확인해 주세요");
  }
  if (input.purpose !== "AUTH" && input.purpose !== "NORMAL" && input.purpose !== "AD") {
    throw new ApiError(400, "INVALID_PURPOSE", "요청 값을 확인해 주세요");
  }
  if (input.fallback !== "NONE" && input.fallback !== "SAME" && input.fallback !== "CUSTOM") {
    throw new ApiError(400, "INVALID_FALLBACK", "요청 값을 확인해 주세요");
  }
  if (input.channel !== "ALIMTALK" && input.fallback !== "NONE") throw new ApiError(400, "FALLBACK_NOT_ALLOWED", "대체발송은 알림톡에서만 쓸 수 있어요");
  if (input.fallback === "CUSTOM" && (input.fallbackBody == null || input.fallbackBody.trim() === "")) {
    throw new ApiError(400, "FALLBACK_BODY_REQUIRED", "대체 문구를 입력해 주세요");
  }
  if (input.channel === "ALIMTALK") {
    if (!input.templateCode) throw new ApiError(400, "TEMPLATE_REQUIRED", "알림톡 템플릿 코드가 필요해요");
    const template = db.templates.find((item) => item.clientId === input.clientId
      && item.templateCode === input.templateCode
      && item.status === "APPROVED");
    if (!template) throw new ApiError(400, "TEMPLATE_NOT_APPROVED", "승인된 알림톡 템플릿만 사용할 수 있어요");
  }
  const senderPhone = compactSender(input.senderNumber ?? "");
  if (!senderPhone) throw new ApiError(400, "INVALID_SENDER", "발신번호 형식을 확인해 주세요");
  const sender = db.senders.find((item) => item.clientId === input.clientId && item.phone === senderPhone && item.status === "APPROVED");
  if (!sender) throw new ApiError(400, "SENDER_NOT_APPROVED", "승인된 발신번호만 사용할 수 있어요");
  if (input.purpose === "AD") {
    const decision = adDecision({
      purpose: "AD",
      scheduledAt: input.scheduledAt ?? new Date().toISOString(),
      body: input.body,
      companyName: input.companyName ?? "",
    });
    if (!decision.hasPrefix) throw new ApiError(400, "AD_PREFIX_REQUIRED", "(광고)로 시작해 주세요");
    if (!decision.hasOptOut) throw new ApiError(400, "AD_OPTOUT_REQUIRED", "무료수신거부 문구가 필요해요");
    if (!decision.hasCompanyName) throw new ApiError(400, "AD_COMPANY_REQUIRED", "본문에 업체명이 필요해요");
    if (decision.inBlockedHours) throw new ApiError(400, "AD_BLOCKED_HOURS", "광고 문자는 21:00–08:00에는 보낼 수 없어요");
  }
  const { prepared, skipped } = prepareRecipients(input);
  const unit = messageRules.unitPrice[input.channel];
  const cost = prepared.reduce((sum, row) => sum + unit + row.fallbackUnit, 0);
  if (cost > db.balance) {
    throw new ApiError(402, "INSUFFICIENT_BALANCE", "잔액이 부족해요", {
      balance: db.balance,
      cost,
      shortfall: cost - db.balance,
    });
  }
  db.balance -= cost;
  const createdAt = new Date().toISOString();
  const id = nextId();
  const counts: CampaignDetail["counts"] = { QUEUED: 0, SENT: 0, DELIVERED: 0, FAILED: 0 };
  let fallbackCount = 0;
  prepared.forEach((row, index) => {
    const common: CommonCode = index % 5 === 0 ? (failPlan[index % failPlan.length] ?? "ETC") : "SUCCESS";
    const status: MessageStatus = common === "SUCCESS" ? "DELIVERED" : "FAILED";
    const carrier = carrierOf(row.phone);
    const messageId = nextId();
    counts[status] += 1;
    db.messages.push({
      id: messageId,
      campaignId: id,
      recipient: row.phone,
      carrier,
      channel: input.channel,
      status,
      carrierCode: carrierCodeFor(carrier, common),
      commonCode: common,
      cost: unit,
      createdAt,
      fallbackOfId: null,
    });
    if (input.channel === "ALIMTALK" && status === "FAILED" && input.fallback !== "NONE") {
      const fallbackText = input.fallback === "SAME" ? row.body : renderTemplate(input.fallbackBody ?? "", row.variables);
      const fallbackChannel = requireText(fallbackText).channel ?? "SMS";
      fallbackCount += 1;
      counts.DELIVERED += 1;
      db.messages.push({
        id: nextId(),
        campaignId: id,
        recipient: row.phone,
        carrier,
        channel: fallbackChannel,
        status: "DELIVERED",
        carrierCode: carrierCodeFor(carrier, "SUCCESS"),
        commonCode: "SUCCESS",
        cost: row.fallbackUnit,
        createdAt,
        fallbackOfId: messageId,
      });
    }
  });
  const campaign: CampaignDetail = {
    id,
    clientId: input.clientId,
    channel: input.channel,
    purpose: input.purpose,
    fallback: input.fallback,
    senderNumber: senderPhone,
    templateCode: input.channel === "ALIMTALK" ? input.templateCode : null,
    scheduledAt: input.scheduledAt,
    estimatedCost: cost,
    createdAt,
    counts,
    fallbackCount,
  };
  db.campaigns.unshift(campaign);
  return {
    id,
    estimatedCost: cost,
    balanceAfter: db.balance,
    messageCount: prepared.length,
    skippedOptOut: skipped,
  };
};

const statsFor = (from: string, to: string, groupBy: StatsGroup): StatsResponse => {
  const rows = db.messages.filter((item) => {
    const day = seoulDay(item.createdAt);
    return day >= from && day <= to;
  });
  const groups = new Map<string, { total: number; failed: number }>();
  for (const row of rows) {
    const key = groupBy === "carrier" ? row.carrier : (row.commonCode ?? "PENDING");
    const current = groups.get(key) ?? { total: 0, failed: 0 };
    current.total += 1;
    if (row.status === "FAILED") current.failed += 1;
    groups.set(key, current);
  }
  return {
    from,
    to,
    groupBy,
    groups: [...groups.entries()].map(([key, value]) => ({
      key,
      total: value.total,
      failed: value.failed,
      failRate: value.total === 0 ? 0 : value.failed / value.total,
    })),
  };
};

const workbookFor = (campaignId: number) => {
  const rows = db.messages
    .filter((item) => item.campaignId === campaignId)
    .sort((left, right) => left.id - right.id)
    .map((item) => {
      const copy = item.commonCode === null ? { explanation: "", action: "" } : copyOf(item.commonCode);
      return {
        번호: item.id,
        수신번호: item.recipient,
        통신사: item.carrier,
        채널: item.channel,
        상태: item.status,
        통신사코드: item.carrierCode ?? "",
        공통코드: item.commonCode ?? "",
        설명: copy.explanation,
        "다음 조치": copy.action,
        "대체발송 원본": item.fallbackOfId ?? "",
      };
    });
  const sheet = XLSX.utils.json_to_sheet(rows, { header: exportHeaders });
  const book = XLSX.utils.book_new();
  XLSX.utils.book_append_sheet(book, sheet, "results");
  return XLSX.write(book, { type: "array", bookType: "xlsx" }) as Uint8Array;
};

const exportView = (job: StoredExport): ExportJob => ({
  id: job.id,
  campaignId: job.campaignId,
  status: job.status,
  downloadUrl: job.status === "DONE" ? `/v1/exports/${job.id}/file` : null,
  error: job.error,
});

export const resetMock = () => {
  db = seed();
};

export const setMockBalance = (amount: number) => {
  db.balance = amount;
};

export const mockRequest = (method: string, path: string, body: unknown) => {
  const url = new URL(path, "http://sendwise.local");
  const pathname = url.pathname;
  if (method === "GET" && pathname === "/v1/rules") return messageRules;
  if (method === "GET" && pathname === "/v1/sender-numbers") {
    const id = clientQuery(url);
    return db.senders.filter((item) => item.clientId === id);
  }
  if (method === "POST" && pathname === "/v1/sender-numbers") {
    const payload = body as { clientId?: number; phone?: string };
    if (payload.clientId == null) throw new ApiError(400, "CLIENT_REQUIRED", "고객사 번호가 필요해요");
    knownClient(payload.clientId);
    const phone = compactSender(String(payload.phone ?? ""));
    if (!phone) throw new ApiError(400, "INVALID_SENDER", "발신번호 형식을 확인해 주세요");
    if (db.senders.some((item) => item.clientId === payload.clientId && item.phone === phone)) {
      throw new ApiError(409, "DUPLICATE_SENDER", "이미 등록된 번호예요");
    }
    const sender: SenderNumber = {
      id: nextId(),
      clientId: payload.clientId,
      phone,
      status: "SUBMITTED",
      rejectReason: null,
    };
    db.senders.unshift(sender);
    return sender;
  }
  const senderMatch = pathname.match(/^\/v1\/sender-numbers\/(\d+)$/);
  if (senderMatch && method === "PATCH") {
    const sender = db.senders.find((item) => item.id === Number(senderMatch[1]));
    if (!sender) throw new ApiError(404, "SENDER_NOT_FOUND", "발신번호를 찾을 수 없어요");
    const patch = body as { status?: string; rejectReason?: string | null };
    const next = `${sender.status}->${patch.status ?? ""}`;
    const allowed = next === "SUBMITTED->REVIEWING" || next === "REVIEWING->APPROVED" || next === "REVIEWING->REJECTED";
    if (!allowed) throw new ApiError(409, "INVALID_STATUS", "이 상태로는 바꿀 수 없어요");
    if (patch.status === "REJECTED" && (patch.rejectReason == null || patch.rejectReason.trim() === "")) {
      throw new ApiError(400, "REJECT_REASON_REQUIRED", "반려 사유를 입력해 주세요");
    }
    sender.status = patch.status as SenderNumber["status"];
    sender.rejectReason = patch.status === "REJECTED" ? (patch.rejectReason ?? null) : null;
    return sender;
  }
  if (senderMatch && method === "DELETE") {
    const id = Number(senderMatch[1]);
    if (!db.senders.some((item) => item.id === id)) throw new ApiError(404, "SENDER_NOT_FOUND", "발신번호를 찾을 수 없어요");
    db.senders = db.senders.filter((item) => item.id !== id);
    return null;
  }
  if (senderMatch && method === "GET") {
    const sender = db.senders.find((item) => item.id === Number(senderMatch[1]));
    if (!sender) throw new ApiError(404, "SENDER_NOT_FOUND", "발신번호를 찾을 수 없어요");
    return sender;
  }
  if (method === "GET" && pathname === "/v1/alimtalk-templates") {
    const id = clientQuery(url);
    return db.templates.filter((item) => item.clientId === id && item.status === "APPROVED");
  }
  if (method === "POST" && pathname === "/v1/campaigns") return createCampaign(body as CampaignCreateInput);
  const campaignMatch = pathname.match(/^\/v1\/campaigns\/(\d+)$/);
  if (campaignMatch && method === "GET") return requireCampaign(Number(campaignMatch[1]));
  const messageMatch = pathname.match(/^\/v1\/campaigns\/(\d+)\/messages$/);
  if (messageMatch && method === "GET") {
    requireCampaign(Number(messageMatch[1]));
    const status = url.searchParams.get("status");
    const page = Number(url.searchParams.get("page") ?? "0");
    const size = Number(url.searchParams.get("size") ?? "20");
    if (!Number.isInteger(page) || !Number.isInteger(size) || page < 0 || size < 1 || size > 100) {
      throw new ApiError(400, "INVALID_PAGE", "페이지 값을 확인해 주세요");
    }
    if (status && status !== "QUEUED" && status !== "SENT" && status !== "DELIVERED" && status !== "FAILED") {
      throw new ApiError(400, "INVALID_STATUS", "상태 값을 확인해 주세요");
    }
    const filtered = db.messages
      .filter((item) => item.campaignId === Number(messageMatch[1]) && (!status || item.status === status))
      .sort((left, right) => left.id - right.id);
    const start = page * size;
    return { page, size, total: filtered.length, items: filtered.slice(start, start + size).map(toMessage) };
  }
  const exportMatch = pathname.match(/^\/v1\/campaigns\/(\d+)\/exports$/);
  if (exportMatch && method === "POST") {
    requireCampaign(Number(exportMatch[1]));
    const job: StoredExport = { id: nextId(), campaignId: Number(exportMatch[1]), status: "PENDING", error: null };
    db.exports.push(job);
    return exportView(job);
  }
  const exportStatus = pathname.match(/^\/v1\/exports\/(\d+)$/);
  if (exportStatus && method === "GET") {
    const job = db.exports.find((item) => item.id === Number(exportStatus[1]));
    if (!job) throw new ApiError(404, "EXPORT_NOT_FOUND", "내려받기 작업을 찾을 수 없어요");
    if (job.status === "PENDING" || job.status === "RUNNING") {
      job.status = "DONE";
      files.set(job.id, workbookFor(job.campaignId));
    }
    return exportView(job);
  }
  if (method === "GET" && pathname === "/v1/stats") {
    const from = url.searchParams.get("from");
    const to = url.searchParams.get("to");
    const groupBy = url.searchParams.get("groupBy");
    if (!from || !to) throw new ApiError(400, "RANGE_REQUIRED", "시작일과 종료일이 필요해요");
    if (to < from) throw new ApiError(400, "INVALID_RANGE", "종료일이 시작일보다 빨라요");
    if (groupBy !== "carrier" && groupBy !== "code") throw new ApiError(400, "INVALID_GROUP", "groupBy는 carrier 또는 code예요");
    return statsFor(from, to, groupBy);
  }
  throw new ApiError(404, "VALIDATION", "요청 주소를 찾지 못했어요");
};

export const mockFile = (path: string) => {
  const match = path.match(/^\/v1\/exports\/(\d+)\/file$/);
  const job = match ? db.exports.find((item) => item.id === Number(match[1])) : undefined;
  if (!job) throw new ApiError(404, "EXPORT_NOT_FOUND", "내려받기 작업을 찾을 수 없어요");
  if (job.status !== "DONE") throw new ApiError(409, "EXPORT_NOT_READY", "엑셀 파일을 아직 만들고 있어요");
  const file = files.get(job.id);
  if (!file) throw new ApiError(409, "EXPORT_NOT_READY", "엑셀 파일을 아직 만들고 있어요");
  return file;
};
