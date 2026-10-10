package com.earntrap.backend.service;

import com.earntrap.backend.dto.RedemptionEligibilityResponse;
import com.earntrap.backend.dto.RedemptionResponse;
import com.earntrap.backend.entity.Redemption;
import com.earntrap.backend.entity.User;
import com.earntrap.backend.entity.UserProgress;
import com.earntrap.backend.enums.RedemptionStatus;
import com.earntrap.backend.repository.LevelCompletionRepository;
import com.earntrap.backend.repository.RedemptionRepository;
import com.earntrap.backend.repository.UserProgressRepository;
import com.earntrap.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RedemptionServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserProgressRepository progressRepository;

    @Mock
    private RedemptionRepository redemptionRepository;

    @Mock
    private LevelCompletionRepository completionRepository;

    @InjectMocks
    private RedemptionService redemptionService;

    private User testUser;
    private UserProgress userProgress;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(1L);
        testUser.setUsername("testuser");
        testUser.setFullName("Test User");
        testUser.setEmail("test@example.com");

        userProgress = new UserProgress(testUser);
        userProgress.setId(10L);
        userProgress.setCurrentLevel(1);
        userProgress.setPoints(0L);
    }

    @Test
    void userBelowLevel18_isNotEligible() {
        userProgress.setCurrentLevel(17);
        userProgress.setPoints(10000L); // plenty of points, but below level 18

        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        when(progressRepository.findByUserId(1L)).thenReturn(Optional.of(userProgress));
        when(completionRepository.sumPointsByUserId(1L)).thenReturn(10000L);
        when(redemptionRepository.findByUserIdOrderByCreatedAtDesc(1L)).thenReturn(List.of());

        RedemptionEligibilityResponse eligibility = redemptionService.getEligibility("testuser");

        assertFalse(eligibility.getLevelEligible());
        assertFalse(eligibility.getEligible());
        assertEquals(17, eligibility.getCurrentLevel());
        assertEquals(18, eligibility.getLevelRequirement());
        assertTrue(eligibility.getMessage().contains("Level 18"));
    }

    @Test
    void userAtLevel18_withBalance100_isNotEligible() {
        userProgress.setCurrentLevel(18);
        userProgress.setPoints(100L); // 100 points = ₹10, well below ₹500

        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        when(progressRepository.findByUserId(1L)).thenReturn(Optional.of(userProgress));
        when(completionRepository.sumPointsByUserId(1L)).thenReturn(100L);
        when(redemptionRepository.findByUserIdOrderByCreatedAtDesc(1L)).thenReturn(List.of());

        RedemptionEligibilityResponse eligibility = redemptionService.getEligibility("testuser");

        assertTrue(eligibility.getLevelEligible());
        assertFalse(eligibility.getBalanceEligible());
        assertFalse(eligibility.getEligible());
        assertEquals(10L, eligibility.getEligibleAmountInRupees());
        assertEquals(100L, eligibility.getRedeemablePoints());
        assertTrue(eligibility.getMessage().contains("Minimum redemption amount is ₹500"));
    }

    @Test
    void userAtLevel18_withBalanceBelow500_isNotEligible() {
        userProgress.setCurrentLevel(18);
        userProgress.setPoints(4999L); // 4999 points = ₹499, which is below ₹500

        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        when(progressRepository.findByUserId(1L)).thenReturn(Optional.of(userProgress));
        when(completionRepository.sumPointsByUserId(1L)).thenReturn(4999L);
        when(redemptionRepository.findByUserIdOrderByCreatedAtDesc(1L)).thenReturn(List.of());

        RedemptionEligibilityResponse eligibility = redemptionService.getEligibility("testuser");

        assertTrue(eligibility.getLevelEligible());
        assertFalse(eligibility.getBalanceEligible());
        assertFalse(eligibility.getEligible());
        assertEquals(499L, eligibility.getEligibleAmountInRupees());
        assertEquals(4999L, eligibility.getRedeemablePoints());
        assertTrue(eligibility.getMessage().contains("Minimum redemption amount is ₹500"));
    }

    @Test
    void userAtLevel18_withExactly500Balance_isEligible() {
        userProgress.setCurrentLevel(18);
        userProgress.setPoints(5000L); // exactly 5000 points = ₹500

        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        when(progressRepository.findByUserId(1L)).thenReturn(Optional.of(userProgress));
        when(completionRepository.sumPointsByUserId(1L)).thenReturn(5000L);
        when(redemptionRepository.findByUserIdOrderByCreatedAtDesc(1L)).thenReturn(List.of());

        RedemptionEligibilityResponse eligibility = redemptionService.getEligibility("testuser");

        assertTrue(eligibility.getLevelEligible());
        assertTrue(eligibility.getBalanceEligible());
        assertTrue(eligibility.getEligible());
        assertEquals(500L, eligibility.getEligibleAmountInRupees());
        assertEquals(5000L, eligibility.getRedeemablePoints());
    }

    @Test
    void userAtLevel18_with6000Balance_isEligible() {
        userProgress.setCurrentLevel(18);
        userProgress.setPoints(6000L); // 6000 points = ₹600

        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        when(progressRepository.findByUserId(1L)).thenReturn(Optional.of(userProgress));
        when(completionRepository.sumPointsByUserId(1L)).thenReturn(6000L);
        when(redemptionRepository.findByUserIdOrderByCreatedAtDesc(1L)).thenReturn(List.of());

        RedemptionEligibilityResponse eligibility = redemptionService.getEligibility("testuser");

        assertTrue(eligibility.getLevelEligible());
        assertTrue(eligibility.getBalanceEligible());
        assertTrue(eligibility.getEligible());
        assertEquals(600L, eligibility.getEligibleAmountInRupees());
        assertEquals(6000L, eligibility.getRedeemablePoints());
    }

    @Test
    void createRedemption_failsWhenBelowLevel18() {
        userProgress.setCurrentLevel(12);
        userProgress.setPoints(10000L);

        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        when(progressRepository.findByUserIdForUpdate(1L)).thenReturn(Optional.of(userProgress));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                redemptionService.createRedemption("testuser", 500L)
        );

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getReason().contains("Level 18"));
    }

    @Test
    void createRedemption_failsWhenAmountBelow500() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                redemptionService.createRedemption("testuser", 499L)
        );

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getReason().contains("Minimum redemption amount"));
    }

    @Test
    void createRedemption_failsWhenInsufficientPoints() {
        userProgress.setCurrentLevel(20);
        userProgress.setPoints(5000L); // user has ₹500, but requests ₹600 (6000 points)

        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        when(progressRepository.findByUserIdForUpdate(1L)).thenReturn(Optional.of(userProgress));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                redemptionService.createRedemption("testuser", 600L)
        );

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getReason().contains("Insufficient points"));
    }

    @Test
    void createRedemption_succeedsAndDeductsPointsAtomically() {
        userProgress.setCurrentLevel(20);
        userProgress.setPoints(7500L); // ₹750

        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        when(progressRepository.findByUserIdForUpdate(1L)).thenReturn(Optional.of(userProgress));
        when(redemptionRepository.save(any(Redemption.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RedemptionResponse res = redemptionService.createRedemption("testuser", 500L);

        assertNotNull(res);
        assertEquals(500L, res.getAmountInRupees());
        assertEquals(5000L, res.getPointsRedeemed());
        assertEquals(RedemptionStatus.PENDING, res.getStatus());
        assertNotNull(res.getReferenceCode());
        assertTrue(res.getReferenceCode().startsWith("RED-"));

        // Verify points deducted: 7500 - 5000 = 2500
        assertEquals(2500L, userProgress.getPoints());
        verify(progressRepository).save(userProgress);
        verify(redemptionRepository).save(any(Redemption.class));
    }

    @Test
    void getRedemptionById_throwsForbiddenForAnotherUser() {
        User otherUser = new User();
        otherUser.setId(99L);
        otherUser.setUsername("other");

        Redemption redemption = new Redemption(otherUser, "RED-TEST", 500L, 5000L);
        redemption.setId(101L);

        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        when(redemptionRepository.findById(101L)).thenReturn(Optional.of(redemption));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                redemptionService.getRedemptionById("testuser", 101L)
        );

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    @Test
    void updateStatus_rejectedRestoresPoints() {
        Redemption redemption = new Redemption(testUser, "RED-REJECT-TEST", 500L, 5000L);
        redemption.setId(102L);
        redemption.setStatus(RedemptionStatus.PENDING);

        userProgress.setPoints(2500L);

        when(redemptionRepository.findById(102L)).thenReturn(Optional.of(redemption));
        when(progressRepository.findByUserIdForUpdate(1L)).thenReturn(Optional.of(userProgress));

        RedemptionResponse updated = redemptionService.updateStatus(
                102L,
                RedemptionStatus.REJECTED,
                null,
                "Incorrect payment details"
        );

        assertEquals(RedemptionStatus.REJECTED, updated.getStatus());
        assertEquals("Incorrect payment details", updated.getAdminNotes());
        // Points restored: 2500 + 5000 = 7500
        assertEquals(7500L, userProgress.getPoints());
        verify(progressRepository).save(userProgress);
    }

    @Test
    void updateStatus_paidDoesNotDoubleDeductPoints() {
        Redemption redemption = new Redemption(testUser, "RED-PAID-TEST", 500L, 5000L);
        redemption.setId(103L);
        redemption.setStatus(RedemptionStatus.PENDING);

        userProgress.setPoints(2500L);

        when(redemptionRepository.findById(103L)).thenReturn(Optional.of(redemption));

        RedemptionResponse updated = redemptionService.updateStatus(
                103L,
                RedemptionStatus.PAID,
                "TXN-BANK-998877",
                "Disbursed via IMPS"
        );

        assertEquals(RedemptionStatus.PAID, updated.getStatus());
        assertEquals("TXN-BANK-998877", updated.getTransactionReference());
        // User progress points should NOT be touched or deducted again
        assertEquals(2500L, userProgress.getPoints());
        verify(progressRepository, never()).save(any());
    }
}
