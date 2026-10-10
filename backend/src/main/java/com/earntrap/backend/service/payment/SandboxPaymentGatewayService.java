package com.earntrap.backend.service.payment;

import com.earntrap.backend.dto.TopUpVerificationRequest;
import com.earntrap.backend.entity.TopUpTransaction;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;

@Service
public class SandboxPaymentGatewayService implements PaymentGatewayService {

    @Value("${earntrap.payment.provider:SANDBOX}")
    private String providerName;

    @Value("${earntrap.payment.sandbox.enabled:true}")
    private boolean sandboxEnabled;

    @Value("${earntrap.payment.key-id:}")
    private String keyId;

    @Value("${earntrap.payment.key-secret:}")
    private String keySecret;

    @Value("${earntrap.payment.webhook-secret:}")
    private String webhookSecret;

    @Override
    public PaymentOrderResult createOrder(TopUpTransaction transaction) {
        String providerOrderId = "order_sbx_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        String checkoutUrl = "/sandbox-checkout?order=" + providerOrderId + "&ref=" + transaction.getOrderReference();
        return new PaymentOrderResult(providerOrderId, checkoutUrl, providerName);
    }

    @Override
    public PaymentVerificationResult verifyPayment(TopUpTransaction transaction, TopUpVerificationRequest request) {
        if (request == null) {
            return PaymentVerificationResult.invalid("Missing payment verification payload");
        }

        // If a cryptographic HMAC signature is supplied, verify it against keySecret
        if (request.getProviderSignature() != null && !request.getProviderSignature().isBlank()) {
            if (keySecret != null && !keySecret.isBlank()) {
                boolean validSignature = verifyHmacSha256(
                        transaction.getProviderOrderId() + "|" + request.getProviderPaymentId(),
                        request.getProviderSignature(),
                        keySecret
                );
                if (validSignature) {
                    return PaymentVerificationResult.success(request.getProviderPaymentId());
                } else {
                    return PaymentVerificationResult.invalid("Payment signature verification failed");
                }
            }
        }

        // Sandbox mode verification
        if (sandboxEnabled) {
            String outcome = request.getOutcome() != null ? request.getOutcome().trim().toUpperCase() : "SUCCESS";
            if ("FAILED".equals(outcome)) {
                return PaymentVerificationResult.failed(
                        request.getProviderPaymentId() != null ? request.getProviderPaymentId() : "pay_sbx_failed",
                        "Transaction declined during sandbox payment"
                );
            } else if ("CANCELLED".equals(outcome)) {
                return PaymentVerificationResult.cancelled("Transaction cancelled by customer");
            } else if ("SUCCESS".equals(outcome)) {
                String paymentId = request.getProviderPaymentId() != null && !request.getProviderPaymentId().isBlank()
                        ? request.getProviderPaymentId()
                        : "pay_sbx_" + UUID.randomUUID().toString().replace("-", "").substring(0, 14);
                return PaymentVerificationResult.success(paymentId);
            } else {
                return PaymentVerificationResult.invalid("Unrecognized payment outcome: " + outcome);
            }
        }

        // If not in sandbox mode and no valid provider signature provided, strictly reject
        return PaymentVerificationResult.invalid(
                "Authoritative payment verification requires valid provider credentials and signature in production mode"
        );
    }

    @Override
    public PaymentVerificationResult verifyWebhook(String payload, String signatureHeader) {
        if (!sandboxEnabled && (webhookSecret == null || webhookSecret.isBlank())) {
            return PaymentVerificationResult.invalid("Webhook secret is not configured");
        }

        if (signatureHeader == null || signatureHeader.isBlank()) {
            if (sandboxEnabled) {
                return PaymentVerificationResult.success("pay_webhook_sbx");
            }
            return PaymentVerificationResult.invalid("Missing webhook signature header");
        }

        if (webhookSecret != null && !webhookSecret.isBlank()) {
            boolean valid = verifyHmacSha256(payload, signatureHeader, webhookSecret);
            if (!valid) {
                return PaymentVerificationResult.invalid("Webhook signature verification failed");
            }
        }

        return PaymentVerificationResult.success("pay_webhook_" + System.currentTimeMillis());
    }

    @Override
    public boolean isSandbox() {
        return sandboxEnabled;
    }

    @Override
    public String getProviderName() {
        return providerName;
    }

    private boolean verifyHmacSha256(String data, String signature, String secret) {
        try {
            Mac sha256Hmac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            sha256Hmac.init(secretKey);
            byte[] hash = sha256Hmac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return MessageDigest.isEqual(
                    hexString.toString().getBytes(StandardCharsets.UTF_8),
                    signature.trim().getBytes(StandardCharsets.UTF_8)
            );
        } catch (Exception e) {
            return false;
        }
    }
}
