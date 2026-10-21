package com.sendwise.api.infra.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "campaign")
public class CampaignEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "client_id", nullable = false)
    private long clientId;

    @Column(nullable = false, length = 16)
    private String channel;

    @Column(nullable = false, length = 16)
    private String purpose;

    @Column(name = "fallback_mode", nullable = false, length = 16)
    private String fallbackMode;

    @Column(nullable = false, length = 4000)
    private String body;

    @Column(name = "fallback_body", length = 4000)
    private String fallbackBody;

    @Column(name = "sender_number", nullable = false, length = 20)
    private String senderNumber;

    @Column(name = "template_code", length = 64)
    private String templateCode;

    @Column(name = "company_name", length = 100)
    private String companyName;

    @Column(name = "scheduled_at")
    private Instant scheduledAt;

    @Column(name = "estimated_cost", nullable = false)
    private long estimatedCost;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public Long getId() {
        return id;
    }

    public long getClientId() {
        return clientId;
    }

    public void setClientId(long clientId) {
        this.clientId = clientId;
    }

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    public String getPurpose() {
        return purpose;
    }

    public void setPurpose(String purpose) {
        this.purpose = purpose;
    }

    public String getFallbackMode() {
        return fallbackMode;
    }

    public void setFallbackMode(String fallbackMode) {
        this.fallbackMode = fallbackMode;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public String getFallbackBody() {
        return fallbackBody;
    }

    public void setFallbackBody(String fallbackBody) {
        this.fallbackBody = fallbackBody;
    }

    public String getSenderNumber() {
        return senderNumber;
    }

    public void setSenderNumber(String senderNumber) {
        this.senderNumber = senderNumber;
    }

    public String getTemplateCode() {
        return templateCode;
    }

    public void setTemplateCode(String templateCode) {
        this.templateCode = templateCode;
    }

    public String getCompanyName() {
        return companyName;
    }

    public void setCompanyName(String companyName) {
        this.companyName = companyName;
    }

    public Instant getScheduledAt() {
        return scheduledAt;
    }

    public void setScheduledAt(Instant scheduledAt) {
        this.scheduledAt = scheduledAt;
    }

    public long getEstimatedCost() {
        return estimatedCost;
    }

    public void setEstimatedCost(long estimatedCost) {
        this.estimatedCost = estimatedCost;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
