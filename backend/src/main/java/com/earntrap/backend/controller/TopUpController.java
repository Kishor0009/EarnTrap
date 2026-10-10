package com.earntrap.backend.controller;

import com.earntrap.backend.dto.TopUpOrderResponse;
import com.earntrap.backend.dto.TopUpRequest;
import com.earntrap.backend.dto.TopUpTransactionResponse;
import com.earntrap.backend.dto.TopUpVerificationRequest;
import com.earntrap.backend.service.TopUpService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/top-ups")
public class TopUpController {

    private final TopUpService topUpService;

    public TopUpController(TopUpService topUpService) {
        this.topUpService = topUpService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TopUpOrderResponse createTopUpOrder(
            @Valid @RequestBody TopUpRequest request,
            Authentication authentication
    ) {
        return topUpService.createTopUpOrder(
                authentication.getName(),
                request.getAmountInRupees()
        );
    }

    @GetMapping("/{id}")
    public TopUpTransactionResponse getTopUpStatus(
            @PathVariable Long id,
            Authentication authentication
    ) {
        return topUpService.getTopUpStatus(authentication.getName(), id);
    }

    @PostMapping("/{id}/verify")
    public TopUpTransactionResponse verifyPayment(
            @PathVariable Long id,
            @RequestBody(required = false) TopUpVerificationRequest request,
            Authentication authentication
    ) {
        return topUpService.verifyAndProcessPayment(
                authentication.getName(),
                id,
                request
        );
    }

    @PostMapping("/webhook")
    public TopUpTransactionResponse handleWebhook(
            @RequestParam(name = "orderId") String orderId,
            @RequestParam(name = "outcome", defaultValue = "SUCCESS") String outcome,
            @RequestHeader(name = "X-Webhook-Signature", required = false) String signature,
            @RequestBody(required = false) String payload
    ) {
        return topUpService.processWebhook(
                orderId,
                payload != null ? payload : "",
                signature,
                outcome
        );
    }
}
