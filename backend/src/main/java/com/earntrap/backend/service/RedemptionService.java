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
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class RedemptionService {

    public static final int LEVEL_REQUIREMENT = 18;
    public static final long MIN_REDEMPTION_AMOUNT_RUPEES = 500L;
    public static final long POINTS_PER_RUPEE = 10L; // 100 points = ₹10 => 10 points = ₹1
    public static final long MIN_POINTS_REQUIRED = MIN_REDEMPTION_AMOUNT_RUPEES * POINTS_PER_RUPEE; // 5000 points

    private final UserRepository userRepository;
    private final UserProgressRepository progressRepository;
    private final RedemptionRepository redemptionRepository;
    private final LevelCompletionRepository completionRepository;

    public RedemptionService(
            UserRepository userRepository,
            UserProgressRepository progressRepository,
            RedemptionRepository redemptionRepository,
            LevelCompletionRepository completionRepository
    ) {
        this.userRepository = userRepository;
        this.progressRepository = progressRepository;
        this.redemptionRepository = redemptionRepository;
        this.completionRepository = completionRepository;
    }

    private User findUserByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "User not found"
                ));
    }

    private UserProgress getOrCreateProgress(User user) {
        return progressRepository.findByUserId(user.getId())
                .orElseGet(() -> progressRepository.save(new UserProgress(user)));
    }

    @Transactional
    public RedemptionEligibilityResponse getEligibility(String username) {
        User user = findUserByUsername(username);
        UserProgress progress = getOrCreateProgress(user);

        int currentLevel = progress.getCurrentLevel();
        boolean levelEligible = currentLevel >= LEVEL_REQUIREMENT;

        long redeemablePoints = progress.getPoints() != null ? progress.getPoints() : 0L;
        long totalEarnedPoints = redeemablePoints;
        try {
            Long completedPoints = completionRepository.sumPointsByUserId(user.getId());
            if (completedPoints != null && completedPoints > totalEarnedPoints) {
                totalEarnedPoints = completedPoints;
            }
        } catch (Exception ignored) {
            // Fallback to progress points
        }

        long eligibleAmount = redeemablePoints / POINTS_PER_RUPEE;
        boolean balanceEligible = eligibleAmount >= MIN_REDEMPTION_AMOUNT_RUPEES;
        boolean eligible = levelEligible && balanceEligible;

        List<Redemption> userRedemptions = redemptionRepository.findByUserIdOrderByCreatedAtDesc(user.getId());
        long paidAmount = userRedemptions.stream()
                .filter(r -> r.getStatus() == RedemptionStatus.PAID)
                .mapToLong(Redemption::getAmountInRupees)
                .sum();
        long pendingAmount = userRedemptions.stream()
                .filter(r -> r.getStatus() == RedemptionStatus.PENDING)
                .mapToLong(Redemption::getAmountInRupees)
                .sum();

        String message;
        if (!levelEligible) {
            message = "Redemption eligibility starts at Level " + LEVEL_REQUIREMENT +
                    ". You are currently at Level " + currentLevel + ".";
        } else if (!balanceEligible) {
            message = "Minimum redemption amount is ₹" + MIN_REDEMPTION_AMOUNT_RUPEES +
                    " (" + MIN_POINTS_REQUIRED + " points). You currently have ₹" +
                    eligibleAmount + " (" + redeemablePoints + " points).";
        } else {
            message = "You are eligible to redeem up to ₹" + eligibleAmount +
                    " (" + redeemablePoints + " points).";
        }

        return new RedemptionEligibilityResponse(
                currentLevel,
                LEVEL_REQUIREMENT,
                levelEligible,
                totalEarnedPoints,
                redeemablePoints,
                (int) POINTS_PER_RUPEE,
                eligibleAmount,
                MIN_REDEMPTION_AMOUNT_RUPEES,
                MIN_POINTS_REQUIRED,
                balanceEligible,
                eligible,
                paidAmount,
                pendingAmount,
                message
        );
    }

    @Transactional(readOnly = true)
    public List<RedemptionResponse> getMyRedemptions(String username) {
        User user = findUserByUsername(username);
        List<Redemption> list = redemptionRepository.findByUserIdOrderByCreatedAtDesc(user.getId());
        return list.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public RedemptionResponse getRedemptionById(String username, Long id) {
        User user = findUserByUsername(username);
        Redemption redemption = redemptionRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Redemption request not found"
                ));

        if (!redemption.getUser().getId().equals(user.getId())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "You are not authorized to view this redemption request"
            );
        }

        return mapToResponse(redemption);
    }

    @Transactional
    public RedemptionResponse createRedemption(String username, Long amountInRupees) {
        if (amountInRupees == null || amountInRupees < MIN_REDEMPTION_AMOUNT_RUPEES) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Minimum redemption amount is ₹" + MIN_REDEMPTION_AMOUNT_RUPEES
            );
        }

        User user = findUserByUsername(username);

        // Lock progress row to avoid concurrent race conditions
        UserProgress progress = progressRepository.findByUserIdForUpdate(user.getId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Progress record not found"
                ));

        if (progress.getCurrentLevel() < LEVEL_REQUIREMENT) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Redemption eligibility starts at Level " + LEVEL_REQUIREMENT +
                            ". Your current level is " + progress.getCurrentLevel()
            );
        }

        long pointsRequired = amountInRupees * POINTS_PER_RUPEE;
        if (progress.getPoints() < pointsRequired) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Insufficient points balance. You have " + progress.getPoints() +
                            " points, but " + pointsRequired + " points are required for ₹" + amountInRupees
            );
        }

        // Deduct points atomically
        progress.setPoints(progress.getPoints() - pointsRequired);
        progressRepository.save(progress);

        String referenceCode = "RED-" + System.currentTimeMillis() + "-" +
                UUID.randomUUID().toString().substring(0, 6).toUpperCase();

        Redemption redemption = new Redemption(
                user,
                referenceCode,
                amountInRupees,
                pointsRequired
        );
        redemptionRepository.save(redemption);

        return mapToResponse(redemption);
    }

    @Transactional
    public RedemptionResponse updateStatus(
            Long redemptionId,
            RedemptionStatus newStatus,
            String transactionReference,
            String adminNotes
    ) {
        Redemption redemption = redemptionRepository.findById(redemptionId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Redemption request not found"
                ));

        RedemptionStatus oldStatus = redemption.getStatus();

        if (oldStatus == newStatus) {
            return mapToResponse(redemption);
        }

        // If request is being REJECTED from PENDING or APPROVED, restore points
        if (newStatus == RedemptionStatus.REJECTED &&
                (oldStatus == RedemptionStatus.PENDING || oldStatus == RedemptionStatus.APPROVED)) {
            UserProgress progress = progressRepository.findByUserIdForUpdate(redemption.getUser().getId())
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.CONFLICT,
                            "Progress record not found"
                    ));
            progress.setPoints(progress.getPoints() + redemption.getPointsRedeemed());
            progressRepository.save(progress);
        }

        redemption.setStatus(newStatus);
        if (transactionReference != null && !transactionReference.isBlank()) {
            redemption.setTransactionReference(transactionReference.trim());
        }
        if (adminNotes != null) {
            redemption.setAdminNotes(adminNotes.trim());
        }

        redemptionRepository.save(redemption);
        return mapToResponse(redemption);
    }

    private RedemptionResponse mapToResponse(Redemption r) {
        return new RedemptionResponse(
                r.getId(),
                r.getReferenceCode(),
                r.getAmountInRupees(),
                r.getPointsRedeemed(),
                r.getStatus(),
                r.getCreatedAt(),
                r.getUpdatedAt(),
                r.getTransactionReference(),
                r.getAdminNotes()
        );
    }
}
