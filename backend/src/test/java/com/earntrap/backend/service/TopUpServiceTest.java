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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TopUpServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserWalletRepository walletRepository;

    @Mock
    private TopUpTransactionRepository transactionRepository;

    @Mock
    private PaymentGatewayService paymentGatewayService;

    @Mock
    private WalletService walletService;

    @InjectMocks
    private TopUpService topUpService;

    private User testUser;
    private User otherUser;
    private UserWallet userWallet;
    private TopUpTransaction pendingTransaction;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(1L);
        testUser.setUsername("testuser");
        testUser.setEmail("test@earntrap.com");

        otherUser = new User();
        otherUser.setId(2L);
        otherUser.setUsername("otheruser");

        userWallet = new UserWallet(testUser);
        userWallet.setId(10L);
        userWallet.setBalanceInRupees(250L);

        pendingTransaction = new TopUpTransaction(testUser, "TOPUP-12345", 500L, "SANDBOX");
        pendingTransaction.setId(100L);
        pendingTransaction.setProviderOrderId("order_sbx_123");
        pendingTransaction.setStatus(TopUpStatus.PENDING);
    }

    @Test
    void createTopUpOrder_validAmount_succeeds() {
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        when(walletService.getOrCreateWallet(testUser)).thenReturn(userWallet);
        when(paymentGatewayService.getProviderName()).thenReturn("SANDBOX");
        when(paymentGatewayService.createOrder(any(TopUpTransaction.class)))
                .thenReturn(new PaymentOrderResult("order_sbx_123", "/checkout", "SANDBOX"));
        when(paymentGatewayService.isSandbox()).thenReturn(true);
        when(transactionRepository.save(any(TopUpTransaction.class))).thenAnswer(inv -> {
            TopUpTransaction t = inv.getArgument(0);
            t.setId(100L);
            return t;
        });

        TopUpOrderResponse res = topUpService.createTopUpOrder("testuser", 500L);

        assertNotNull(res);
        assertEquals(500L, res.getAmountInRupees());
        assertEquals("INR", res.getCurrency());
        assertEquals(TopUpStatus.PENDING, res.getStatus());
        assertEquals("order_sbx_123", res.getProviderOrderId());
        assertTrue(res.isSandboxMode());
        verify(transactionRepository).save(any(TopUpTransaction.class));
    }

    @Test
    void createTopUpOrder_amountBelowMinimum_throwsBadRequest() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                topUpService.createTopUpOrder("testuser", 99L)
        );
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getReason().contains("Minimum top up amount"));
    }

    @Test
    void createTopUpOrder_amountAboveMaximum_throwsBadRequest() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                topUpService.createTopUpOrder("testuser", 100001L)
        );
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getReason().contains("Maximum top up amount"));
    }

    @Test
    void createTopUpOrder_nullAmount_throwsBadRequest() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                topUpService.createTopUpOrder("testuser", null)
        );
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void getTopUpStatus_forOwner_succeeds() {
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        when(transactionRepository.findById(100L)).thenReturn(Optional.of(pendingTransaction));
        when(walletService.mapToResponse(pendingTransaction)).thenReturn(new TopUpTransactionResponse(
                100L, "TOPUP-12345", 500L, "INR", TopUpStatus.PENDING,
                "SANDBOX", "order_sbx_123", null, null, null, null, null
        ));

        TopUpTransactionResponse res = topUpService.getTopUpStatus("testuser", 100L);
        assertNotNull(res);
        assertEquals("TOPUP-12345", res.getOrderReference());
    }

    @Test
    void getTopUpStatus_forDifferentUser_throwsForbidden() {
        when(userRepository.findByUsername("otheruser")).thenReturn(Optional.of(otherUser));
        when(transactionRepository.findById(100L)).thenReturn(Optional.of(pendingTransaction));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                topUpService.getTopUpStatus("otheruser", 100L)
        );
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    @Test
    void verifyAndProcessPayment_successfulVerification_creditsWalletAtomically() {
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        when(transactionRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(pendingTransaction));
        when(paymentGatewayService.verifyPayment(eq(pendingTransaction), any(TopUpVerificationRequest.class)))
                .thenReturn(PaymentVerificationResult.success("pay_sbx_abc123"));
        when(walletRepository.findByUserIdForUpdate(1L)).thenReturn(Optional.of(userWallet));

        TopUpVerificationRequest req = new TopUpVerificationRequest("pay_sbx_abc123", "sig_valid", "SUCCESS");

        topUpService.verifyAndProcessPayment("testuser", 100L, req);

        // Initial balance 250 + 500 top-up = 750
        assertEquals(750L, userWallet.getBalanceInRupees());
        assertEquals(TopUpStatus.SUCCESS, pendingTransaction.getStatus());
        assertEquals("pay_sbx_abc123", pendingTransaction.getProviderPaymentId());
        assertNotNull(pendingTransaction.getCreditedAt());

        verify(walletRepository).save(userWallet);
        verify(transactionRepository).save(pendingTransaction);
    }

    @Test
    void verifyAndProcessPayment_duplicateCallback_isIdempotentAndDoesNotDoubleCredit() {
        pendingTransaction.setStatus(TopUpStatus.SUCCESS);
        pendingTransaction.setCreditedAt(LocalDateTime.now().minusMinutes(5));

        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        when(transactionRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(pendingTransaction));

        TopUpVerificationRequest req = new TopUpVerificationRequest("pay_sbx_abc123", "sig_valid", "SUCCESS");

        topUpService.verifyAndProcessPayment("testuser", 100L, req);

        // Wallet must NOT be touched or credited twice!
        verify(walletRepository, never()).findByUserIdForUpdate(any());
        verify(walletRepository, never()).save(any());
        verify(paymentGatewayService, never()).verifyPayment(any(), any());
    }

    @Test
    void verifyAndProcessPayment_paymentFailure_marksStatusFailedWithoutCrediting() {
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        when(transactionRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(pendingTransaction));
        when(paymentGatewayService.verifyPayment(eq(pendingTransaction), any(TopUpVerificationRequest.class)))
                .thenReturn(PaymentVerificationResult.failed("pay_failed", "Card declined by issuing bank"));

        TopUpVerificationRequest req = new TopUpVerificationRequest("pay_failed", null, "FAILED");

        topUpService.verifyAndProcessPayment("testuser", 100L, req);

        assertEquals(TopUpStatus.FAILED, pendingTransaction.getStatus());
        assertEquals("Card declined by issuing bank", pendingTransaction.getFailureReason());
        assertNull(pendingTransaction.getCreditedAt());

        // Wallet is untouched
        verify(walletRepository, never()).save(any());
        verify(transactionRepository).save(pendingTransaction);
    }

    @Test
    void verifyAndProcessPayment_paymentCancelled_marksStatusCancelledWithoutCrediting() {
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        when(transactionRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(pendingTransaction));
        when(paymentGatewayService.verifyPayment(eq(pendingTransaction), any(TopUpVerificationRequest.class)))
                .thenReturn(PaymentVerificationResult.cancelled("User abandoned checkout"));

        TopUpVerificationRequest req = new TopUpVerificationRequest(null, null, "CANCELLED");

        topUpService.verifyAndProcessPayment("testuser", 100L, req);

        assertEquals(TopUpStatus.CANCELLED, pendingTransaction.getStatus());
        assertNull(pendingTransaction.getCreditedAt());

        // Wallet is untouched
        verify(walletRepository, never()).save(any());
        verify(transactionRepository).save(pendingTransaction);
    }

    @Test
    void verifyAndProcessPayment_forDifferentUser_throwsForbidden() {
        when(userRepository.findByUsername("otheruser")).thenReturn(Optional.of(otherUser));
        when(transactionRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(pendingTransaction));

        TopUpVerificationRequest req = new TopUpVerificationRequest("pay_fake", "sig_fake", "SUCCESS");

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                topUpService.verifyAndProcessPayment("otheruser", 100L, req)
        );
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        verify(walletRepository, never()).save(any());
    }

    @Test
    void verifyAndProcessPayment_invalidSignatureOrForgedClaim_throwsBadRequest() {
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        when(transactionRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(pendingTransaction));
        when(paymentGatewayService.verifyPayment(eq(pendingTransaction), any(TopUpVerificationRequest.class)))
                .thenReturn(PaymentVerificationResult.invalid("Invalid payment signature"));

        TopUpVerificationRequest forgedReq = new TopUpVerificationRequest("pay_tampered", "sig_forged", "SUCCESS");

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                topUpService.verifyAndProcessPayment("testuser", 100L, forgedReq)
        );

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getReason().contains("Invalid payment signature"));
        verify(walletRepository, never()).save(any());
    }

    @Test
    void processWebhook_successfulWebhook_creditsWallet() {
        when(paymentGatewayService.verifyWebhook(anyString(), anyString()))
                .thenReturn(PaymentVerificationResult.success("pay_webhook_999"));
        when(transactionRepository.findByProviderOrderId("order_sbx_123"))
                .thenReturn(Optional.of(pendingTransaction));
        when(walletRepository.findByUserIdForUpdate(1L))
                .thenReturn(Optional.of(userWallet));

        topUpService.processWebhook("order_sbx_123", "{}", "sig_valid", "SUCCESS");

        assertEquals(750L, userWallet.getBalanceInRupees());
        assertEquals(TopUpStatus.SUCCESS, pendingTransaction.getStatus());
        assertNotNull(pendingTransaction.getCreditedAt());
        verify(walletRepository).save(userWallet);
    }

    @Test
    void processWebhook_duplicateWebhook_isIdempotent() {
        pendingTransaction.setStatus(TopUpStatus.SUCCESS);
        pendingTransaction.setCreditedAt(LocalDateTime.now().minusHours(1));

        when(paymentGatewayService.verifyWebhook(anyString(), anyString()))
                .thenReturn(PaymentVerificationResult.success("pay_webhook_999"));
        when(transactionRepository.findByProviderOrderId("order_sbx_123"))
                .thenReturn(Optional.of(pendingTransaction));

        topUpService.processWebhook("order_sbx_123", "{}", "sig_valid", "SUCCESS");

        // Wallet must not be saved again
        verify(walletRepository, never()).findByUserIdForUpdate(any());
        verify(walletRepository, never()).save(any());
    }

    @Test
    void processWebhook_invalidSignature_throwsBadRequest() {
        when(paymentGatewayService.verifyWebhook(anyString(), anyString()))
                .thenReturn(PaymentVerificationResult.invalid("Invalid webhook signature"));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                topUpService.processWebhook("order_sbx_123", "{}", "sig_invalid", "SUCCESS")
        );

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }
}
