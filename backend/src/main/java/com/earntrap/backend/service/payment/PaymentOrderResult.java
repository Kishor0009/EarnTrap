package com.earntrap.backend.service.payment;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class PaymentOrderResult {
    private final String providerOrderId;
    private final String checkoutUrl;
    private final String provider;
}
