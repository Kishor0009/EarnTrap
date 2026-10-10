package com.earntrap.backend.service;

import com.earntrap.backend.dto.TopUpTransactionResponse;
import com.earntrap.backend.dto.WalletResponse;
import com.earntrap.backend.entity.TopUpTransaction;
import com.earntrap.backend.entity.User;
import com.earntrap.backend.entity.UserWallet;
import com.earntrap.backend.repository.TopUpTransactionRepository;
import com.earntrap.backend.repository.UserRepository;
import com.earntrap.backend.repository.UserWalletRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class WalletService {

    private final UserRepository userRepository;
    private final UserWalletRepository walletRepository;
    private final TopUpTransactionRepository transactionRepository;

    public WalletService(
            UserRepository userRepository,
            UserWalletRepository walletRepository,
            TopUpTransactionRepository transactionRepository
    ) {
        this.userRepository = userRepository;
        this.walletRepository = walletRepository;
        this.transactionRepository = transactionRepository;
    }

    private User findUserByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "User not found"
                ));
    }

    @Transactional
    public UserWallet getOrCreateWallet(User user) {
        return walletRepository.findByUserId(user.getId())
                .orElseGet(() -> walletRepository.save(new UserWallet(user)));
    }

    @Transactional
    public WalletResponse getMyWallet(String username) {
        User user = findUserByUsername(username);
        UserWallet wallet = getOrCreateWallet(user);
        return new WalletResponse(
                wallet.getBalanceInRupees(),
                wallet.getCurrency(),
                wallet.getUpdatedAt()
        );
    }

    @Transactional(readOnly = true)
    public List<TopUpTransactionResponse> getMyTransactions(String username) {
        User user = findUserByUsername(username);
        List<TopUpTransaction> list = transactionRepository.findByUserIdOrderByCreatedAtDesc(user.getId());
        return list.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public TopUpTransactionResponse mapToResponse(TopUpTransaction t) {
        return new TopUpTransactionResponse(
                t.getId(),
                t.getOrderReference(),
                t.getAmountInRupees(),
                t.getCurrency(),
                t.getStatus(),
                t.getPaymentProvider(),
                t.getProviderOrderId(),
                t.getProviderPaymentId(),
                t.getFailureReason(),
                t.getCreditedAt(),
                t.getCreatedAt(),
                t.getUpdatedAt()
        );
    }
}
