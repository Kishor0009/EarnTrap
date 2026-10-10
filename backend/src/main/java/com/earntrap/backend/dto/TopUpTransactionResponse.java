package com.earntrap.backend.dto;

import com.earntrap.backend.enums.TopUpStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TopUpTransactionResponse {
    private Long id;
    private String orderReference;
    private Long amountInRupees;
    private String currency;
    private TopUpStatus status;
    private String paymentProvider;
    private String providerOrderId;
    private String providerPaymentId;
    private String failureReason;
    private LocalDateTime creditedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
