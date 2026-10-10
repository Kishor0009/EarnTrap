package com.earntrap.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TopUpVerificationRequest {
    private String providerPaymentId;
    private String providerSignature;
    private String outcome; // Optional for sandbox testing: "SUCCESS", "FAILED", "CANCELLED"
}
