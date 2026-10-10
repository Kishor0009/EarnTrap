package com.earntrap.backend.service.payment;

import com.earntrap.backend.enums.TopUpStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class PaymentVerificationResult {
    private final boolean verified;
    private final TopUpStatus status;
    private final String providerPaymentId;
    private final String failureReason;

    public static PaymentVerificationResult success(String providerPaymentId) {
        return new PaymentVerificationResult(true, TopUpStatus.SUCCESS, providerPaymentId, null);
    }

    public static PaymentVerificationResult failed(String providerPaymentId, String reason) {
        return new PaymentVerificationResult(true, TopUpStatus.FAILED, providerPaymentId, reason);
    }

    public static PaymentVerificationResult cancelled(String reason) {
        return new PaymentVerificationResult(true, TopUpStatus.CANCELLED, null, reason != null ? reason : "Payment cancelled by user");
    }

    public static PaymentVerificationResult invalid(String reason) {
        return new PaymentVerificationResult(false, null, null, reason);
    }
}
