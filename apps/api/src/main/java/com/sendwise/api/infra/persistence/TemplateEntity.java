package com.sendwise.api.infra.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "alimtalk_template")
public class TemplateEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "client_id", nullable = false)
    private long clientId;

    @Column(name = "template_code", nullable = false, length = 64)
    private String templateCode;

    @Column(nullable = false, length = 2000)
    private String body;

    @Column(name = "variables_json", nullable = false, length = 1000)
    private String variablesJson;

    @Column(nullable = false, length = 16)
    private String status;

    public Long getId() {
        return id;
    }

    public long getClientId() {
        return clientId;
    }

    public String getTemplateCode() {
        return templateCode;
    }

    public String getBody() {
        return body;
    }

    public String getVariablesJson() {
        return variablesJson;
    }

    public String getStatus() {
        return status;
    }
}
