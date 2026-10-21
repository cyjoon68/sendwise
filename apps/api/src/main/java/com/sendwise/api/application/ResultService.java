package com.sendwise.api.application;

import com.sendwise.api.domain.ApiError;
import com.sendwise.api.domain.dispatch.OutboundItem;
import com.sendwise.api.domain.dispatch.OutboundQueue;
import com.sendwise.api.domain.rules.CodeCopy;
import com.sendwise.api.domain.rules.MessageRules;
import com.sendwise.api.domain.rules.TextMeasure;
import com.sendwise.api.infra.balance.JdbcAccounts;
import com.sendwise.api.infra.persistence.CampaignEntity;
import com.sendwise.api.infra.persistence.MessageEntity;
import com.sendwise.api.infra.persistence.MessageRepository;
import com.sendwise.api.infra.rules.RuleTables;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Service
public class ResultService {
    private static final Logger log = LoggerFactory.getLogger(ResultService.class);

    private final MessageRepository messages;
    private final JdbcAccounts accounts;
    private final RuleTables tables;
    private final OutboundQueue queue;
    private final Clock clock;

    public ResultService(
            MessageRepository messages,
            JdbcAccounts accounts,
            RuleTables tables,
            OutboundQueue queue,
            Clock clock
    ) {
        this.messages = messages;
        this.accounts = accounts;
        this.tables = tables;
        this.queue = queue;
        this.clock = clock;
    }

    @Transactional
    public Map<String, Object> apply(String carrier, long messageId, String code, String reportedCommon) {
        if (code == null || code.isBlank()) {
            throw new ApiError(400, "CODE_REQUIRED", "통신사 결과 코드가 필요해요");
        }
        MessageEntity message = messages.lockById(messageId)
                .orElseThrow(() -> new ApiError(404, "MESSAGE_NOT_FOUND", "메시지를 찾을 수 없어요"));
        if ("DELIVERED".equals(message.getStatus()) || "FAILED".equals(message.getStatus())) {
            return view(message, messages.findFirstByFallbackOfId(message.getId()).map(MessageEntity::getId).orElse(null));
        }
        if (!"QUEUED".equals(message.getStatus()) && !"SENT".equals(message.getStatus())) {
            throw new ApiError(409, "INVALID_STATUS", "이 상태의 메시지에는 결과를 반영할 수 없어요");
        }
        String common = tables.codeBook().toCommon(carrier.toUpperCase(Locale.ROOT), code);
        if (reportedCommon != null && !reportedCommon.isBlank() && !reportedCommon.equals(common)) {
            log.warn("callback commonCode {} differs from mapped {} for message {}", reportedCommon, common, messageId);
        }
        message.setCarrierCode(code);
        message.setCommonCode(common);
        CampaignEntity campaign = message.getCampaign();
        Long fallbackId = null;
        if ("SUCCESS".equals(common)) {
            message.setStatus("DELIVERED");
            refund(campaign.getClientId(), campaign.getId(), message.getId(), message.getReservedCost());
            message.setReservedCost(0);
        } else {
            message.setStatus("FAILED");
            refund(campaign.getClientId(), campaign.getId(), message.getId(), message.getCost());
            boolean fallback = "ALIMTALK".equals(message.getChannel())
                    && campaign.getFallbackMode() != null
                    && !"NONE".equals(campaign.getFallbackMode())
                    && message.getReservedCost() > 0
                    && messages.countByFallbackOfId(message.getId()) == 0;
            if (fallback) {
                fallbackId = createFallback(message, campaign);
            } else {
                refund(campaign.getClientId(), campaign.getId(), message.getId(), message.getReservedCost());
                message.setReservedCost(0);
            }
        }
        return view(message, fallbackId);
    }

    @Transactional
    public void markSent(long messageId) {
        messages.markSent(messageId);
    }

    @Transactional
    public void markRejected(long messageId) {
        MessageEntity message = messages.lockById(messageId).orElse(null);
        if (message == null) {
            return;
        }
        if ("DELIVERED".equals(message.getStatus()) || "FAILED".equals(message.getStatus())) {
            return;
        }
        if (!"QUEUED".equals(message.getStatus()) && !"SENT".equals(message.getStatus())) {
            return;
        }
        message.setStatus("FAILED");
        CampaignEntity campaign = message.getCampaign();
        refund(campaign.getClientId(), campaign.getId(), message.getId(), message.getCost());
        refund(campaign.getClientId(), campaign.getId(), message.getId(), message.getReservedCost());
        message.setReservedCost(0);
    }

    private Long createFallback(MessageEntity message, CampaignEntity campaign) {
        String text = "SAME".equals(campaign.getFallbackMode()) ? message.getBody() : campaign.getFallbackBody();
        MessageRules rules = tables.rules();
        TextMeasure.Result sized = tables.measure().measure(text == null ? "" : text);
        long reserved = message.getReservedCost();
        if (!sized.encodable() || sized.overLimit() || sized.channel() == null) {
            refund(campaign.getClientId(), campaign.getId(), message.getId(), reserved);
            message.setReservedCost(0);
            return null;
        }
        long actual = rules.price(sized.channel());
        if (actual > reserved) {
            long extra = actual - reserved;
            if (!accounts.debitIfEnough(campaign.getClientId(), extra)) {
                refund(campaign.getClientId(), campaign.getId(), message.getId(), reserved);
                message.setReservedCost(0);
                return null;
            }
            accounts.ledger(campaign.getClientId(), campaign.getId(), message.getId(), "DEBIT", extra, clock.instant());
        } else if (actual < reserved) {
            refund(campaign.getClientId(), campaign.getId(), message.getId(), reserved - actual);
        }
        MessageEntity child = new MessageEntity();
        child.setCampaign(campaign);
        child.setRecipient(message.getRecipient());
        child.setCarrier(message.getCarrier());
        child.setChannel(sized.channel());
        child.setBody(text);
        child.setStatus("QUEUED");
        child.setFallbackOfId(message.getId());
        child.setCost(actual);
        child.setReservedCost(0);
        child.setPurpose(message.getPurpose());
        child.setCreatedAt(clock.instant());
        messages.save(child);
        message.setReservedCost(0);
        OutboundItem item = new OutboundItem(
                child.getId(),
                child.getCarrier(),
                child.getChannel(),
                child.getRecipient(),
                child.getBody(),
                null,
                child.getPurpose(),
                0,
                campaign.getScheduledAt()
        );
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    queue.enqueue(item);
                }
            });
        } else {
            queue.enqueue(item);
        }
        return child.getId();
    }

    private void refund(long clientId, long campaignId, long messageId, long amount) {
        if (amount <= 0) {
            return;
        }
        accounts.credit(clientId, amount);
        accounts.ledger(clientId, campaignId, messageId, "REFUND", amount, clock.instant());
    }

    private Map<String, Object> view(MessageEntity message, Long fallbackId) {
        CodeCopy copy = CodeCopy.of(message.getCommonCode());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("messageId", message.getId());
        body.put("status", message.getStatus());
        body.put("carrierCode", message.getCarrierCode());
        body.put("commonCode", message.getCommonCode());
        body.put("explanation", copy.explanation());
        body.put("actionHint", copy.actionHint());
        body.put("fallbackMessageId", fallbackId);
        return body;
    }
}
