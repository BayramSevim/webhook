package com.github.webhook.domain.entity;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "delivery_attempts")
public class DeliveryAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "delivery_id", nullable = false, updatable = false)
    private Delivery delivery;

    @Column(nullable = false)
    private int attemptNumber;

    @Column(nullable = false)
    private Instant attemptedAt;

    @Column(nullable = false)
    private int durationMs;

    /** Karsi taraftan cevap gelmediyse (timeout, baglanti hatasi) null. */
    private Integer responseStatus;

    private String errorMessage;

    protected DeliveryAttempt() {
        // JPA icin
    }

    public DeliveryAttempt(Delivery delivery, int attemptNumber, Instant attemptedAt,
                           int durationMs, Integer responseStatus, String errorMessage) {
        this.delivery = delivery;
        this.attemptNumber = attemptNumber;
        this.attemptedAt = attemptedAt;
        this.durationMs = durationMs;
        this.responseStatus = responseStatus;
        this.errorMessage = errorMessage;
    }

    public Long getId() { return id; }
    public Delivery getDelivery() { return delivery; }
    public int getAttemptNumber() { return attemptNumber; }
    public Instant getAttemptedAt() { return attemptedAt; }
    public int getDurationMs() { return durationMs; }
    public Integer getResponseStatus() { return responseStatus; }
    public String getErrorMessage() { return errorMessage; }
}
