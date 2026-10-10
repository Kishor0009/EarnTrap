package com.earntrap.backend.controller;

import com.earntrap.backend.dto.TopUpOrderResponse;
import com.earntrap.backend.dto.TopUpRequest;
import com.earntrap.backend.dto.TopUpTransactionResponse;
import com.earntrap.backend.dto.WalletResponse;
import com.earntrap.backend.enums.TopUpStatus;
import com.earntrap.backend.service.TopUpService;
import com.earntrap.backend.service.WalletService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class TopUpControllerTest {

    private MockMvc mockMvc;

    @Mock
    private TopUpService topUpService;

    @Mock
    private WalletService walletService;

    @InjectMocks
    private TopUpController topUpController;

    @InjectMocks
    private WalletController walletController;

    private ObjectMapper objectMapper;
    private Authentication testAuth;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(topUpController, walletController)
                .build();
        objectMapper = new ObjectMapper();
        testAuth = new UsernamePasswordAuthenticationToken("testuser", "password", Collections.emptyList());
    }

    @Test
    void getWallet_returnsCurrentConfirmedBalance() throws Exception {
        when(walletService.getMyWallet("testuser"))
                .thenReturn(new WalletResponse(500L, "INR", LocalDateTime.now()));

        mockMvc.perform(get("/api/wallet/me").principal(testAuth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balanceInRupees").value(500))
                .andExpect(jsonPath("$.currency").value("INR"));
    }

    @Test
    void getTransactions_returnsTransactionHistory() throws Exception {
        TopUpTransactionResponse tx = new TopUpTransactionResponse(
                1L, "TOPUP-123", 250L, "INR", TopUpStatus.SUCCESS,
                "SANDBOX", "order_123", "pay_123", null, LocalDateTime.now(),
                LocalDateTime.now(), LocalDateTime.now()
        );
        when(walletService.getMyTransactions("testuser")).thenReturn(List.of(tx));

        mockMvc.perform(get("/api/wallet/transactions").principal(testAuth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].orderReference").value("TOPUP-123"))
                .andExpect(jsonPath("$[0].amountInRupees").value(250))
                .andExpect(jsonPath("$[0].status").value("SUCCESS"));
    }

    @Test
    void createTopUpOrder_validAmount_returns201Created() throws Exception {
        TopUpOrderResponse orderResponse = new TopUpOrderResponse(
                10L, "TOPUP-999", 500L, "INR", TopUpStatus.PENDING,
                "SANDBOX", "order_sbx_999", "/checkout", true, LocalDateTime.now()
        );

        when(topUpService.createTopUpOrder(eq("testuser"), eq(500L)))
                .thenReturn(orderResponse);

        TopUpRequest req = new TopUpRequest(500L);

        mockMvc.perform(post("/api/top-ups")
                        .principal(testAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderReference").value("TOPUP-999"))
                .andExpect(jsonPath("$.amountInRupees").value(500))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.sandboxMode").value(true));
    }

    @Test
    void createTopUpOrder_invalidAmountBelow100_returns400BadRequest() throws Exception {
        TopUpRequest req = new TopUpRequest(50L);

        mockMvc.perform(post("/api/top-ups")
                        .principal(testAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getTopUpStatus_returnsOrderDetails() throws Exception {
        TopUpTransactionResponse tx = new TopUpTransactionResponse(
                10L, "TOPUP-999", 500L, "INR", TopUpStatus.PENDING,
                "SANDBOX", "order_sbx_999", null, null, null,
                LocalDateTime.now(), LocalDateTime.now()
        );

        when(topUpService.getTopUpStatus("testuser", 10L)).thenReturn(tx);

        mockMvc.perform(get("/api/top-ups/10").principal(testAuth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderReference").value("TOPUP-999"))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }
}
