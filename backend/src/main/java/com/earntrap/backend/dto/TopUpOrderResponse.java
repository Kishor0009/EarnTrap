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
public class TopUpOrderResponse {
    private Long id;
    private String orderReference;
    private Long amountInRupees;
    private String currency;
    private TopUpStatus status;
    private String paymentProvider;
    private String providerOrderId;
    private String checkoutUrl;
    private boolean sandboxMode;
    private LocalDateTime createdAt;
}
