package com.earntrap.backend.controller;

import com.earntrap.backend.dto.RedemptionEligibilityResponse;
import com.earntrap.backend.dto.RedemptionRequest;
import com.earntrap.backend.dto.RedemptionResponse;
import com.earntrap.backend.service.RedemptionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/redemptions")
public class RedemptionController {

    private final RedemptionService redemptionService;

    public RedemptionController(RedemptionService redemptionService) {
        this.redemptionService = redemptionService;
    }

    @GetMapping("/eligibility")
    public RedemptionEligibilityResponse getEligibility(
            Authentication authentication
    ) {
        return redemptionService.getEligibility(authentication.getName());
    }

    @GetMapping
    public List<RedemptionResponse> getMyRedemptions(
            Authentication authentication
    ) {
        return redemptionService.getMyRedemptions(authentication.getName());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RedemptionResponse createRedemption(
            @Valid @RequestBody RedemptionRequest request,
            Authentication authentication
    ) {
        return redemptionService.createRedemption(
                authentication.getName(),
                request.getAmountInRupees()
        );
    }

    @GetMapping("/{id}")
    public RedemptionResponse getRedemptionById(
            @PathVariable Long id,
            Authentication authentication
    ) {
        return redemptionService.getRedemptionById(authentication.getName(), id);
    }
}
