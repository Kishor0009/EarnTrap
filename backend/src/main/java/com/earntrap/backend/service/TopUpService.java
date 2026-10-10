package com.earntrap.backend.service;

import com.earntrap.backend.dto.TopUpOrderResponse;
import com.earntrap.backend.dto.TopUpTransactionResponse;
import com.earntrap.backend.dto.TopUpVerificationRequest;
import com.earntrap.backend.entity.TopUpTransaction;
import com.earntrap.backend.entity.User;
import com.earntrap.backend.entity.UserWallet;
import com.earntrap.backend.enums.TopUpStatus;
import com.earntrap.backend.repository.TopUpTransactionRepository;
import com.earntrap.backend.repository.UserRepository;
import com.earntrap.backend.repository.UserWalletRepository;
import com.earntrap.backend.service.payment.PaymentGatewayService;
import com.earntrap.backend.service.payment.PaymentOrderResult;
import com.earntrap.backend.service.payment.PaymentVerificationResult;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class TopUpService {

    public static final long MIN_TOPUP_AMOUNT = 100L;
    public static final long MAX_TOPUP_AMOUNT = 100000L;

    private final UserRepository userRepository;
    private final UserWalletRepository walletRepository;
    private final TopUpTransactionRepository transactionRepository;
    private final PaymentGatewayService paymentGatewayService;
    private final WalletService walletService;

    public TopUpService(
            UserRepository userRepository,
            UserWalletRepository walletRepository,
            TopUpTransactionRepository transactionRepository,
            PaymentGatewayService paymentGatewayService,
            WalletService walletService
    ) {
        this.userRepository = userRepository;
        this.walletRepository = walletRepository;
        this.transactionRepository = transactionRepository;
        this.paymentGatewayService = paymentGatewayService;
        this.walletService = walletService;
    }

    private User findUserByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "User not found"
                ));
    }

    @Transactional
    public TopUpOrderResponse createTopUpOrder(String username, Long amountInRupees) {
        if (amountInRupees == null || amountInRupees < MIN_TOPUP_AMOUNT) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Minimum top up amount is ₹" + MIN_TOPUP_AMOUNT
            );
        }
        if (amountInRupees > MAX_TOPUP_AMOUNT) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Maximum top up amount is ₹" + MAX_TOPUP_AMOUNT
            );
        }

        User user = findUserByUsername(username);

        // Ensure user wallet exists
        walletService.getOrCreateWallet(user);

        String orderReference = "TOPUP-" + System.currentTimeMillis() + "-" +
                UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        TopUpTransaction transaction = new TopUpTransaction(
                user,
                orderReference,
                amountInRupees,
                paymentGatewayService.getProviderName()
        );

        PaymentOrderResult orderResult = paymentGatewayService.createOrder(transaction);
        transaction.setProviderOrderId(orderResult.getProviderOrderId());

        TopUpTransaction saved = transactionRepository.save(transaction);

        return new TopUpOrderResponse(
                saved.getId(),
                saved.getOrderReference(),
                saved.getAmountInRupees(),
                saved.getCurrency(),
                saved.getStatus(),
                saved.getPaymentProvider(),
                saved.getProviderOrderId(),
                orderResult.getCheckoutUrl(),
                paymentGatewayService.isSandbox(),
                saved.getCreatedAt()
        );
    }

    @Transactional(readOnly = true)
    public TopUpTransactionResponse getTopUpStatus(String username, Long id) {
        User user = findUserByUsername(username);
        TopUpTransaction transaction = transactionRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Top up order not found"
                ));

        if (!transaction.getUser().getId().equals(user.getId())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "You are not authorized to view this transaction"
            );
        }

        return walletService.mapToResponse(transaction);
    }

    @Transactional
    public TopUpTransactionResponse verifyAndProcessPayment(
            String username,
            Long id,
            TopUpVerificationRequest request
    ) {
        User user = findUserByUsername(username);

        // Pessimistic lock transaction record to prevent concurrent double-credit attempts
        TopUpTransaction transaction = transactionRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Top up order not found"
                ));

        if (!transaction.getUser().getId().equals(user.getId())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "You are not authorized to verify this transaction"
            );
        }

        // Idempotency: If already credited, return current state without modifying wallet balance
        if (transaction.getStatus() == TopUpStatus.SUCCESS && transaction.getCreditedAt() != null) {
            return walletService.mapToResponse(transaction);
        }

        if (transaction.getStatus() == TopUpStatus.CANCELLED) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Transaction has already been cancelled"
            );
        }

        if (transaction.getStatus() == TopUpStatus.FAILED) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Transaction has already failed"
            );
        }

        PaymentVerificationResult verification = paymentGatewayService.verifyPayment(transaction, request);

        if (!verification.isVerified()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    verification.getFailureReason() != null
                            ? verification.getFailureReason()
                            : "Payment verification failed"
            );
        }

        if (verification.getStatus() == TopUpStatus.SUCCESS) {
            // Lock wallet row for update
            UserWallet wallet = walletRepository.findByUserIdForUpdate(user.getId())
                    .orElseGet(() -> walletRepository.save(new UserWallet(user)));

            wallet.setBalanceInRupees(wallet.getBalanceInRupees() + transaction.getAmountInRupees());
            walletRepository.save(wallet);

            transaction.setStatus(TopUpStatus.SUCCESS);
            transaction.setCreditedAt(LocalDateTime.now());
            transaction.setProviderPaymentId(verification.getProviderPaymentId());
            if (request != null && request.getProviderSignature() != null) {
                transaction.setProviderSignature(request.getProviderSignature());
            }
            transactionRepository.save(transaction);
        } else if (verification.getStatus() == TopUpStatus.CANCELLED) {
            transaction.setStatus(TopUpStatus.CANCELLED);
            transaction.setFailureReason(verification.getFailureReason());
            transactionRepository.save(transaction);
        } else if (verification.getStatus() == TopUpStatus.FAILED) {
            transaction.setStatus(TopUpStatus.FAILED);
            transaction.setFailureReason(verification.getFailureReason());
            transactionRepository.save(transaction);
        }

        return walletService.mapToResponse(transaction);
    }

    @Transactional
    public TopUpTransactionResponse processWebhook(
            String providerOrderId,
            String payload,
            String signatureHeader,
            String outcome
    ) {
        PaymentVerificationResult verification = paymentGatewayService.verifyWebhook(payload, signatureHeader);
        if (!verification.isVerified()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid webhook signature"
            );
        }

        TopUpTransaction transaction = transactionRepository.findByProviderOrderId(providerOrderId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Order not found for providerOrderId: " + providerOrderId
                ));

        // Idempotency: If already credited, return current state
        if (transaction.getStatus() == TopUpStatus.SUCCESS && transaction.getCreditedAt() != null) {
            return walletService.mapToResponse(transaction);
        }

        if ("SUCCESS".equalsIgnoreCase(outcome) || verification.getStatus() == TopUpStatus.SUCCESS) {
            UserWallet wallet = walletRepository.findByUserIdForUpdate(transaction.getUser().getId())
                    .orElseGet(() -> walletRepository.save(new UserWallet(transaction.getUser())));

            wallet.setBalanceInRupees(wallet.getBalanceInRupees() + transaction.getAmountInRupees());
            walletRepository.save(wallet);

            transaction.setStatus(TopUpStatus.SUCCESS);
            transaction.setCreditedAt(LocalDateTime.now());
            transaction.setProviderPaymentId(verification.getProviderPaymentId());
            transactionRepository.save(transaction);
        } else if ("FAILED".equalsIgnoreCase(outcome)) {
            transaction.setStatus(TopUpStatus.FAILED);
            transaction.setFailureReason("Declined via provider webhook");
            transactionRepository.save(transaction);
        }

        return walletService.mapToResponse(transaction);
    }
}
