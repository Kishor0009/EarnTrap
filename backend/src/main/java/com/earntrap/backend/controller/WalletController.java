package com.earntrap.backend.controller;

import com.earntrap.backend.dto.TopUpTransactionResponse;
import com.earntrap.backend.dto.WalletResponse;
import com.earntrap.backend.service.WalletService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/wallet")
public class WalletController {

    private final WalletService walletService;

    public WalletController(WalletService walletService) {
        this.walletService = walletService;
    }

    @GetMapping("/me")
    public WalletResponse getMyWallet(Authentication authentication) {
        return walletService.getMyWallet(authentication.getName());
    }

    @GetMapping("/transactions")
    public List<TopUpTransactionResponse> getMyTransactions(Authentication authentication) {
        return walletService.getMyTransactions(authentication.getName());
    }
}
