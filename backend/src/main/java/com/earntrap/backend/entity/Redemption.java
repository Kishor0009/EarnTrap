package com.earntrap.backend.entity;

import com.earntrap.backend.enums.RedemptionStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "redemptions",
        indexes = {
                @Index(name = "idx_redemption_user_id", columnList = "user_id"),
                @Index(name = "idx_redemption_status", columnList = "status"),
                @Index(name = "idx_redemption_ref_code", columnList = "reference_code")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class Redemption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reference_code", nullable = false, unique = true, length = 64)
    private String referenceCode;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "amount_in_rupees", nullable = false)
    private Long amountInRupees;

    @Column(name = "points_redeemed", nullable = false)
    private Long pointsRedeemed;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private RedemptionStatus status = RedemptionStatus.PENDING;

    @Column(name = "transaction_reference", length = 128)
    private String transactionReference;

    @Column(name = "admin_notes", length = 255)
    private String adminNotes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public Redemption(
            User user,
            String referenceCode,
            Long amountInRupees,
            Long pointsRedeemed
    ) {
        this.user = user;
        this.referenceCode = referenceCode;
        this.amountInRupees = amountInRupees;
        this.pointsRedeemed = pointsRedeemed;
        this.status = RedemptionStatus.PENDING;
    }

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
        if (status == null) {
            status = RedemptionStatus.PENDING;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
