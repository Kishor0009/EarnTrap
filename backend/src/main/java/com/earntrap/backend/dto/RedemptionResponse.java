package com.earntrap.backend.dto;

import com.earntrap.backend.enums.RedemptionStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RedemptionResponse {

    private Long id;
    private String referenceCode;
    private Long amountInRupees;
    private Long pointsRedeemed;
    private RedemptionStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String transactionReference;
    private String adminNotes;
}
