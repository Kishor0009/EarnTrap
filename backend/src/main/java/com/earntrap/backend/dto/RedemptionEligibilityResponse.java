package com.earntrap.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RedemptionEligibilityResponse {

    private Integer currentLevel;
    private Integer levelRequirement;
    private Boolean levelEligible;
    private Long totalEarnedPoints;
    private Long redeemablePoints;
    private Integer conversionRatePointsPerRupee;
    private Long eligibleAmountInRupees;
    private Long minRedemptionAmountInRupees;
    private Long minRedemptionPoints;
    private Boolean balanceEligible;
    private Boolean eligible;
    private Long totalPaidAmountInRupees;
    private Long totalPendingAmountInRupees;
    private String message;
}
