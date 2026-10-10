package com.earntrap.backend.dto;

import jakarta.validation.constraints.Max;
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
public class TopUpRequest {

    @NotNull(message = "Top up amount is required")
    @Min(value = 100, message = "Minimum top up amount is ₹100")
    @Max(value = 100000, message = "Maximum top up amount is ₹1,00,000")
    private Long amountInRupees;
}
