package com.earntrap.backend.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RedemptionRequest {

    @NotNull(message = "Redemption amount is required")
    @Min(value = 500, message = "Minimum redemption amount is ₹500")
    private Long amountInRupees;
}
