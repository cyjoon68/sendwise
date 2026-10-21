package com.sendwise.api.infra.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "opt_out")
public class OptOutEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "client_id", nullable = false)
    private long clientId;

    @Column(nullable = false, length = 20)
    private String phone;

    public Long getId() {
        return id;
    }

    public long getClientId() {
        return clientId;
    }

    public String getPhone() {
        return phone;
    }
}
