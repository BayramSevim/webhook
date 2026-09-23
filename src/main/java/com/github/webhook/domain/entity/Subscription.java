package com.github.webhook.domain.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "subscriptions")
public class Subscription {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 100)
    private String tenantId;

    @Column(nullable = false)
    private String url;

    @Column(nullable = false)
    private String secret;

    // Postgres text[] kolonu
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(nullable = false)
    private List<String> eventTypes = new ArrayList<>();

    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected Subscription() {
        // JPA icin
    }

    public Subscription(String tenantId, String url, String secret, List<String> eventTypes) {
        this.tenantId = tenantId;
        this.url = url;
        this.secret = secret;
        this.eventTypes = new ArrayList<>(eventTypes);
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getTenantId() { return tenantId; }
    public String getUrl() { return url; }
    public String getSecret() { return secret; }
    public List<String> getEventTypes() { return List.copyOf(eventTypes); }
    public boolean isActive() { return active; }
    public Instant getCreatedAt() { return createdAt; }
}