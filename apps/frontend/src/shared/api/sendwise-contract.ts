export type SenderStatus = "SUBMITTED" | "REVIEWING" | "APPROVED" | "REJECTED";
export type Purpose = "AUTH" | "NORMAL" | "AD";
export type FallbackMode = "NONE" | "SAME" | "CUSTOM";
export type CarrierName = "SKT" | "KT" | "LGU";
export type MessageStatus = "QUEUED" | "SENT" | "DELIVERED" | "FAILED";
export type TextChannel = "SMS" | "LMS";
export type Channel = TextChannel | "ALIMTALK";
export type ExportStatus = "PENDING" | "RUNNING" | "DONE" | "FAILED";

export type SenderNumber = {
  id: number;
  clientId: number;
  phone: string;
  status: SenderStatus;
  rejectReason: string | null;
};

export type AlimtalkTemplate = {
  id: number;
  clientId: number;
  templateCode: string;
  body: string;
  variables: string[];
  status: "APPROVED";
};

export type RecipientInput = {
  phone: string;
  variables?: Record<string, string>;
};

export type CampaignCreateInput = {
  clientId: number;
  channel: Channel;
  purpose: Purpose;
  body: string;
  fallback: FallbackMode;
  fallbackBody: string | null;
  senderNumber: string;
  templateCode: string | null;
  companyName: string | null;
  scheduledAt: string | null;
  recipients: RecipientInput[];
};

export type CampaignCreateResponse = {
  id: number;
  estimatedCost: number;
  balanceAfter: number;
  messageCount: number;
  skippedOptOut: string[];
};

export type CampaignCounts = Record<MessageStatus, number>;

export type CampaignDetail = {
  id: number;
  clientId: number;
  channel: Channel;
  purpose: Purpose;
  fallback: FallbackMode;
  senderNumber: string;
  templateCode: string | null;
  scheduledAt: string | null;
  estimatedCost: number;
  createdAt: string;
  counts: CampaignCounts;
  fallbackCount: number;
};

export type CampaignMessage = {
  id: number;
  recipient: string;
  carrier: CarrierName;
  channel: Channel;
  status: MessageStatus;
  carrierCode: string | null;
  commonCode: string | null;
  explanation: string | null;
  actionHint: string | null;
  fallbackOfId: number | null;
  cost: number;
};

export type MessagePage = {
  page: number;
  size: number;
  total: number;
  items: CampaignMessage[];
};

export type ExportJob = {
  id: number;
  campaignId: number;
  status: ExportStatus;
  downloadUrl: string | null;
  error: string | null;
};

export type StatsGroup = "carrier" | "code";

export type StatsBucket = {
  key: string;
  total: number;
  failed: number;
  failRate: number;
};

export type StatsResponse = {
  from: string;
  to: string;
  groupBy: StatsGroup;
  groups: StatsBucket[];
};

export class ApiError extends Error {
  status: number;
  error: string;
  balance?: number;
  cost?: number;
  shortfall?: number;

  constructor(
    status: number,
    error: string,
    message: string,
    extra?: { balance?: number; cost?: number; shortfall?: number },
  ) {
    super(message);
    this.status = status;
    this.error = error;
    this.balance = extra?.balance;
    this.cost = extra?.cost;
    this.shortfall = extra?.shortfall;
  }
}
