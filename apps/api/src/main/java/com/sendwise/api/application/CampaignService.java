package com.sendwise.api.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sendwise.api.domain.ApiError;
import com.sendwise.api.domain.dispatch.OutboundItem;
import com.sendwise.api.domain.dispatch.OutboundQueue;
import com.sendwise.api.domain.rules.AdPolicy;
import com.sendwise.api.domain.rules.CodeCopy;
import com.sendwise.api.domain.rules.MessageRules;
import com.sendwise.api.domain.rules.PhoneNumbers;
import com.sendwise.api.domain.rules.TemplateRender;
import com.sendwise.api.domain.rules.TextMeasure;
import com.sendwise.api.infra.balance.JdbcAccounts;
import com.sendwise.api.infra.persistence.CampaignEntity;
import com.sendwise.api.infra.persistence.CampaignRepository;
import com.sendwise.api.infra.persistence.MessageEntity;
import com.sendwise.api.infra.persistence.MessageRepository;
import com.sendwise.api.infra.persistence.OptOutRepository;
import com.sendwise.api.infra.persistence.SenderNumberRepository;
import com.sendwise.api.infra.persistence.TemplateRepository;
import com.sendwise.api.infra.rules.RuleTables;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class CampaignService {
    private static final Set<String> CHANNELS = Set.of("SMS", "LMS", "ALIMTALK");
    private static final Set<String> PURPOSES = Set.of("AUTH", "NORMAL", "AD");
    private static final Set<String> FALLBACKS = Set.of("NONE", "SAME", "CUSTOM");
    private static final List<String> STATUSES = List.of("QUEUED", "SENT", "DELIVERED", "FAILED");

    private final RuleTables tables;
    private final JdbcAccounts accounts;
    private final CampaignRepository campaigns;
    private final MessageRepository messages;
    private final SenderNumberRepository senders;
    private final TemplateRepository templates;
    private final OptOutRepository optOuts;
    private final OutboundQueue queue;
    private final Clock clock;
    private final ObjectMapper mapper;

    public CampaignService(
            RuleTables tables,
            JdbcAccounts accounts,
            CampaignRepository campaigns,
            MessageRepository messages,
            SenderNumberRepository senders,
            TemplateRepository templates,
            OptOutRepository optOuts,
            OutboundQueue queue,
            Clock clock,
            ObjectMapper mapper
    ) {
        this.tables = tables;
        this.accounts = accounts;
        this.campaigns = campaigns;
        this.messages = messages;
        this.senders = senders;
        this.templates = templates;
        this.optOuts = optOuts;
        this.queue = queue;
        this.clock = clock;
        this.mapper = mapper;
    }

    @Transactional
    public Map<String, Object> create(CreateCampaign command) {
        if (command.clientId() == null) {
            throw new ApiError(400, "CLIENT_REQUIRED", "고객사 번호가 필요해요");
        }
        if (command.body() == null || command.body().isBlank()) {
            throw new ApiError(400, "BODY_REQUIRED", "본문을 입력해 주세요");
        }
        MessageRules rules = tables.rules();
        TextMeasure measure = tables.measure();
        String channel = token(command.channel(), CHANNELS, "CHANNEL");
        String purpose = token(command.purpose(), PURPOSES, "PURPOSE");
        String fallback = token(command.fallback(), FALLBACKS, "FALLBACK");
        if (!"ALIMTALK".equals(channel) && !"NONE".equals(fallback)) {
            throw new ApiError(400, "FALLBACK_NOT_ALLOWED", "대체발송은 알림톡에서만 쓸 수 있어요");
        }
        Instant when = command.scheduledAt() == null ? clock.instant() : command.scheduledAt();
        AdPolicy.check(purpose, command.body(), command.companyName(), when, rules);
        if ("CUSTOM".equals(fallback) && (command.fallbackBody() == null || command.fallbackBody().isBlank())) {
            throw new ApiError(400, "FALLBACK_BODY_REQUIRED", "대체 문구를 입력해 주세요");
        }
        if ("ALIMTALK".equals(channel)) {
            if (command.templateCode() == null || command.templateCode().isBlank()) {
                throw new ApiError(400, "TEMPLATE_REQUIRED", "알림톡 템플릿 코드가 필요해요");
            }
            templates.findByClientIdAndTemplateCodeAndStatus(command.clientId(), command.templateCode(), "APPROVED")
                    .orElseThrow(() -> new ApiError(400, "TEMPLATE_NOT_APPROVED", "승인된 알림톡 템플릿만 사용할 수 있어요"));
        }
        String sender = PhoneNumbers.compact(command.senderNumber());
        if (!PhoneNumbers.sender(sender)) {
            throw new ApiError(400, "INVALID_SENDER", "발신번호 형식을 확인해 주세요");
        }
        senders.findByClientIdAndPhoneAndStatus(command.clientId(), sender, "APPROVED")
                .orElseThrow(() -> new ApiError(400, "SENDER_NOT_APPROVED", "승인된 발신번호만 사용할 수 있어요"));
        if (!accounts.clientExists(command.clientId())) {
            throw new ApiError(404, "CLIENT_NOT_FOUND", "고객사를 찾을 수 없어요");
        }
        List<Prepared> recipients = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (RecipientRow row : recipientRows(command.recipients())) {
            PhoneNumbers.Normalized phone = PhoneNumbers.normalize(row.phone());
            if (!phone.valid()) {
                throw new ApiError(400, "INVALID_RECIPIENT", "수신번호 형식을 확인해 주세요");
            }
            if (!seen.add(phone.normalized())) {
                continue;
            }
            if (optOuts.existsByClientIdAndPhone(command.clientId(), phone.normalized())) {
                skipped.add(phone.normalized());
                continue;
            }
            String text = TemplateRender.render(command.body(), row.variables());
            if (text.isBlank()) {
                throw new ApiError(400, "BODY_REQUIRED", "본문을 입력해 주세요");
            }
            if ("SMS".equals(channel) || "LMS".equals(channel)) {
                requireChannel(measure, text, channel);
            }
            long fallbackUnit = 0;
            if (!"NONE".equals(fallback)) {
                String fallbackText = "SAME".equals(fallback)
                        ? text
                        : TemplateRender.render(command.fallbackBody(), row.variables());
                if (fallbackText.isBlank()) {
                    throw new ApiError(400, "FALLBACK_BODY_REQUIRED", "대체 문구를 입력해 주세요");
                }
                fallbackUnit = rules.price(requireText(measure, fallbackText).channel());
            }
            recipients.add(new Prepared(phone.normalized(), text, variablesJson(row.variables()), fallbackUnit));
        }
        if (recipients.isEmpty()) {
            throw new ApiError(400, "NO_RECIPIENTS", "보낼 수 있는 수신번호가 없어요");
        }
        long unit = rules.price(channel);
        long cost = recipients.stream().mapToLong(row -> unit + row.fallbackUnit()).sum();
        if (!accounts.debitIfEnough(command.clientId(), cost)) {
            long have = accounts.amount(command.clientId());
            throw new ApiError(402, "INSUFFICIENT_BALANCE", "잔액이 부족해요", Map.of(
                    "balance", have,
                    "cost", cost,
                    "shortfall", Math.max(0, cost - have)
            ));
        }
        Instant now = clock.instant();
        CampaignEntity campaign = new CampaignEntity();
        campaign.setClientId(command.clientId());
        campaign.setChannel(channel);
        campaign.setPurpose(purpose);
        campaign.setFallbackMode(fallback);
        campaign.setBody(command.body());
        campaign.setFallbackBody(command.fallbackBody());
        campaign.setSenderNumber(sender);
        campaign.setTemplateCode(command.templateCode());
        campaign.setCompanyName(command.companyName());
        campaign.setScheduledAt(command.scheduledAt());
        campaign.setEstimatedCost(cost);
        campaign.setCreatedAt(now);
        campaigns.save(campaign);
        List<OutboundItem> outbound = new ArrayList<>();
        for (Prepared recipient : recipients) {
            MessageEntity message = new MessageEntity();
            message.setCampaign(campaign);
            message.setRecipient(recipient.phone());
            message.setCarrier(PhoneNumbers.carrier(recipient.phone()));
            message.setChannel(channel);
            message.setBody(recipient.body());
            message.setTemplateCode("ALIMTALK".equals(channel) ? command.templateCode() : null);
            message.setVariablesJson(recipient.variablesJson());
            message.setStatus("QUEUED");
            message.setCost(unit);
            message.setReservedCost(recipient.fallbackUnit());
            message.setPurpose(purpose);
            message.setCreatedAt(now);
            messages.save(message);
            outbound.add(new OutboundItem(
                    message.getId(),
                    message.getCarrier(),
                    channel,
                    recipient.phone(),
                    recipient.body(),
                    message.getTemplateCode(),
                    "ALIMTALK".equals(channel) ? recipient.variablesJson() : null,
                    purpose,
                    0,
                    command.scheduledAt()
            ));
        }
        accounts.ledger(command.clientId(), campaign.getId(), null, "DEBIT", cost, now);
        enqueueAfterCommit(outbound);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", campaign.getId());
        body.put("estimatedCost", cost);
        body.put("balanceAfter", accounts.amount(command.clientId()));
        body.put("messageCount", recipients.size());
        body.put("skippedOptOut", skipped);
        return body;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> get(long id) {
        CampaignEntity campaign = campaigns.findById(id)
                .orElseThrow(() -> new ApiError(404, "CAMPAIGN_NOT_FOUND", "캠페인을 찾을 수 없어요"));
        Map<String, Long> counts = new LinkedHashMap<>();
        for (String status : STATUSES) {
            counts.put(status, 0L);
        }
        for (Object[] row : messages.countByStatus(id)) {
            counts.put((String) row[0], (Long) row[1]);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", campaign.getId());
        body.put("clientId", campaign.getClientId());
        body.put("channel", campaign.getChannel());
        body.put("purpose", campaign.getPurpose());
        body.put("fallback", campaign.getFallbackMode());
        body.put("senderNumber", campaign.getSenderNumber());
        body.put("templateCode", campaign.getTemplateCode());
        body.put("scheduledAt", campaign.getScheduledAt());
        body.put("estimatedCost", campaign.getEstimatedCost());
        body.put("createdAt", campaign.getCreatedAt());
        body.put("counts", counts);
        body.put("fallbackCount", messages.countFallbacks(id));
        return body;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> messages(long id, String status, int page, int size) {
        if (!campaigns.existsById(id)) {
            throw new ApiError(404, "CAMPAIGN_NOT_FOUND", "캠페인을 찾을 수 없어요");
        }
        if (page < 0 || size < 1 || size > 100) {
            throw new ApiError(400, "INVALID_PAGE", "페이지 값을 확인해 주세요");
        }
        if (status != null && !STATUSES.contains(status)) {
            throw new ApiError(400, "INVALID_STATUS", "상태 값을 확인해 주세요");
        }
        PageRequest request = PageRequest.of(page, size, Sort.by("id"));
        Page<MessageEntity> result = status == null
                ? messages.findByCampaign_Id(id, request)
                : messages.findByCampaign_IdAndStatus(id, status, request);
        List<Map<String, Object>> items = result.getContent().stream().map(this::messageView).toList();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("page", page);
        body.put("size", size);
        body.put("total", result.getTotalElements());
        body.put("items", items);
        return body;
    }

    private Map<String, Object> messageView(MessageEntity message) {
        CodeCopy copy = CodeCopy.of(message.getCommonCode());
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", message.getId());
        item.put("recipient", message.getRecipient());
        item.put("carrier", message.getCarrier());
        item.put("channel", message.getChannel());
        item.put("status", message.getStatus());
        item.put("carrierCode", message.getCarrierCode());
        item.put("commonCode", message.getCommonCode());
        item.put("explanation", message.getCommonCode() == null && !"FAILED".equals(message.getStatus()) ? null : copy.explanation());
        item.put("actionHint", message.getCommonCode() == null && !"FAILED".equals(message.getStatus()) ? null : copy.actionHint());
        item.put("fallbackOfId", message.getFallbackOfId());
        item.put("cost", message.getCost());
        return item;
    }

    private void requireChannel(TextMeasure measure, String text, String channel) {
        TextMeasure.Result sized = requireText(measure, text);
        if (!channel.equals(sized.channel())) {
            throw new ApiError(400, "CHANNEL_MISMATCH", "이 본문은 " + sized.channel() + "로 보내야 해요");
        }
    }

    private TextMeasure.Result requireText(TextMeasure measure, String text) {
        TextMeasure.Result sized = measure.measure(text);
        if (!sized.encodable()) {
            throw new ApiError(400, "UNENCODABLE", "완성형에 없는 문자가 있어요");
        }
        if (sized.overLimit() || sized.channel() == null) {
            throw new ApiError(400, "BODY_TOO_LONG", "본문이 2,000바이트를 넘어요");
        }
        return sized;
    }

    private String token(String value, Set<String> allowed, String code) {
        if (value == null || !allowed.contains(value)) {
            throw new ApiError(400, "INVALID_" + code, "요청 값을 확인해 주세요");
        }
        return value;
    }

    private void enqueueAfterCommit(List<OutboundItem> items) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            items.forEach(queue::enqueue);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                items.forEach(queue::enqueue);
            }
        });
    }

    private List<RecipientRow> recipientRows(JsonNode node) {
        if (node == null || node.isNull() || !node.isArray()) {
            throw new ApiError(400, "RECIPIENTS_REQUIRED", "수신번호를 입력해 주세요");
        }
        List<RecipientRow> rows = new ArrayList<>();
        for (JsonNode item : node) {
            if (item.isTextual()) {
                rows.add(new RecipientRow(item.asText(), null));
                continue;
            }
            if (!item.isObject()) {
                throw new ApiError(400, "INVALID_RECIPIENT", "수신번호 형식을 확인해 주세요");
            }
            String phone = item.path("phone").asText(null);
            if (phone == null || phone.isBlank()) {
                phone = item.path("recipient").asText(null);
            }
            rows.add(new RecipientRow(phone, variablesOf(item.get("variables"))));
        }
        return rows;
    }

    private Map<String, String> variablesOf(JsonNode values) {
        if (values == null || values.isNull()) {
            return null;
        }
        if (!values.isObject()) {
            throw new ApiError(400, "INVALID_VARIABLES", "변수 값을 확인해 주세요");
        }
        Map<String, String> variables = new LinkedHashMap<>();
        values.fields().forEachRemaining(entry -> variables.put(
                entry.getKey(),
                entry.getValue().isNull() ? "" : entry.getValue().asText()
        ));
        return variables.isEmpty() ? null : variables;
    }

    private String variablesJson(Map<String, String> variables) {
        if (variables == null || variables.isEmpty()) {
            return null;
        }
        try {
            return mapper.writeValueAsString(variables);
        } catch (JsonProcessingException exception) {
            throw new ApiError(400, "INVALID_VARIABLES", "변수 값을 확인해 주세요");
        }
    }

    private record RecipientRow(String phone, Map<String, String> variables) {
    }

    private record Prepared(String phone, String body, String variablesJson, long fallbackUnit) {
    }

    public record CreateCampaign(
            Long clientId,
            String channel,
            String purpose,
            String body,
            String fallback,
            String fallbackBody,
            String senderNumber,
            String templateCode,
            String companyName,
            Instant scheduledAt,
            JsonNode recipients
    ) {
    }
}
