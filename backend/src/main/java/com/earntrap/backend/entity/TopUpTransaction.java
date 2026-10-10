package com.earntrap.backend.entity;

import com.earntrap.backend.enums.TopUpStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "top_up_transactions",
        indexes = {
                @Index(name = "idx_topup_user_id", columnList = "user_id"),
                @Index(name = "idx_topup_status", columnList = "status"),
                @Index(name = "idx_topup_order_ref", columnList = "order_reference"),
                @Index(name = "idx_topup_provider_order_id", columnList = "provider_order_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class TopUpTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_reference", nullable = false, unique = true, length = 64)
    private String orderReference;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "amount_in_rupees", nullable = false)
    private Long amountInRupees;

    @Column(nullable = false, length = 10)
    private String currency = "INR";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private TopUpStatus status = TopUpStatus.PENDING;

    @Column(name = "payment_provider", nullable = false, length = 32)
    private String paymentProvider = "SANDBOX";

    @Column(name = "provider_order_id", length = 128)
    private String providerOrderId;

    @Column(name = "provider_payment_id", length = 128)
    private String providerPaymentId;

    @Column(name = "provider_signature", length = 255)
    private String providerSignature;

    @Column(name = "failure_reason", length = 255)
    private String failureReason;

    @Column(name = "credited_at")
    private LocalDateTime creditedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public TopUpTransaction(
            User user,
            String orderReference,
            Long amountInRupees,
            String paymentProvider
    ) {
        this.user = user;
        this.orderReference = orderReference;
        this.amountInRupees = amountInRupees;
        this.paymentProvider = paymentProvider != null ? paymentProvider : "SANDBOX";
        this.currency = "INR";
        this.status = TopUpStatus.PENDING;
    }

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
        if (currency == null) {
            currency = "INR";
        }
        if (status == null) {
            status = TopUpStatus.PENDING;
        }
        if (paymentProvider == null) {
            paymentProvider = "SANDBOX";
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
