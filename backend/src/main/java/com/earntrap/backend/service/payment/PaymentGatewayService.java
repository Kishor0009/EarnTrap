package com.earntrap.backend.service.payment;

import com.earntrap.backend.dto.TopUpVerificationRequest;
import com.earntrap.backend.entity.TopUpTransaction;

public interface PaymentGatewayService {

    PaymentOrderResult createOrder(TopUpTransaction transaction);

    PaymentVerificationResult verifyPayment(TopUpTransaction transaction, TopUpVerificationRequest request);

    PaymentVerificationResult verifyWebhook(String payload, String signatureHeader);

    boolean isSandbox();

    String getProviderName();
}
