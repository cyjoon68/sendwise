package com.sendwise.api.infra.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "message")
public class MessageEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "campaign_id")
    private CampaignEntity campaign;

    @Column(nullable = false, length = 20)
    private String recipient;

    @Column(nullable = false, length = 8)
    private String carrier;

    @Column(nullable = false, length = 16)
    private String channel;

    @Column(nullable = false, length = 4000)
    private String body;

    @Column(name = "template_code", length = 64)
    private String templateCode;

    @Column(name = "variables_json", length = 4000)
    private String variablesJson;

    @Column(name = "carrier_code", length = 32)
    private String carrierCode;

    @Column(name = "common_code", length = 32)
    private String commonCode;

    @Column(nullable = false, length = 16)
    private String status;

    @Column(name = "fallback_of_id")
    private Long fallbackOfId;

    @Column(nullable = false)
    private long cost;

    @Column(name = "reserved_cost", nullable = false)
    private long reservedCost;

    @Column(nullable = false, length = 16)
    private String purpose;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public Long getId() {
        return id;
    }

    public CampaignEntity getCampaign() {
        return campaign;
    }

    public void setCampaign(CampaignEntity campaign) {
        this.campaign = campaign;
    }

    public String getRecipient() {
        return recipient;
    }

    public void setRecipient(String recipient) {
        this.recipient = recipient;
    }

    public String getCarrier() {
        return carrier;
    }

    public void setCarrier(String carrier) {
        this.carrier = carrier;
    }

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public String getTemplateCode() {
        return templateCode;
    }

    public void setTemplateCode(String templateCode) {
        this.templateCode = templateCode;
    }

    public String getVariablesJson() {
        return variablesJson;
    }

    public void setVariablesJson(String variablesJson) {
        this.variablesJson = variablesJson;
    }

    public String getCarrierCode() {
        return carrierCode;
    }

    public void setCarrierCode(String carrierCode) {
        this.carrierCode = carrierCode;
    }

    public String getCommonCode() {
        return commonCode;
    }

    public void setCommonCode(String commonCode) {
        this.commonCode = commonCode;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getFallbackOfId() {
        return fallbackOfId;
    }

    public void setFallbackOfId(Long fallbackOfId) {
        this.fallbackOfId = fallbackOfId;
    }

    public long getCost() {
        return cost;
    }

    public void setCost(long cost) {
        this.cost = cost;
    }

    public long getReservedCost() {
        return reservedCost;
    }

    public void setReservedCost(long reservedCost) {
        this.reservedCost = reservedCost;
    }

    public String getPurpose() {
        return purpose;
    }

    public void setPurpose(String purpose) {
        this.purpose = purpose;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
