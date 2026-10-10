
package com.earntrap.backend.service;

import com.earntrap.backend.dto.LevelCompletionResponse;
import com.earntrap.backend.dto.LevelResponse;
import com.earntrap.backend.dto.ProgressResponse;
import com.earntrap.backend.entity.LevelCompletion;
import com.earntrap.backend.entity.User;
import com.earntrap.backend.entity.UserProgress;
import com.earntrap.backend.repository.LevelCompletionRepository;
import com.earntrap.backend.repository.UserProgressRepository;
import com.earntrap.backend.repository.UserRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;

@Service
public class LevelService {

    private final UserRepository userRepository;
    private final UserProgressRepository progressRepository;
    private final LevelCompletionRepository completionRepository;

    public LevelService(
            UserRepository userRepository,
            UserProgressRepository progressRepository,
            LevelCompletionRepository completionRepository
    ) {
        this.userRepository = userRepository;
        this.progressRepository = progressRepository;
        this.completionRepository = completionRepository;
    }

    // Find a user and initialize progress if it does not exist.
    private UserProgress getOrCreateProgress(String username) {

        User user = userRepository.findByUsername(username)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "User not found"
                        )
                );

        return progressRepository.findByUserId(user.getId())
                .orElseGet(() ->
                        progressRepository.save(new UserProgress(user))
                );
    }

    // Get the authenticated user's current progress.
    @Transactional
    public ProgressResponse getMyProgress(String username) {

        UserProgress progress = getOrCreateProgress(username);

        return new ProgressResponse(
                progress.getCurrentLevel(),
                progress.getPoints(),
                Boolean.TRUE.equals(progress.getTurboPassActive())
        );
    }

    // Get all 60 levels with public/default status (unauthenticated view).
    public List<LevelResponse> getPublicLevels() {
        List<LevelResponse> levels = new ArrayList<>();

        for (int number = 1; number <= 60; number++) {
            String status;
            if (number == 46) {
                status = "COMING_SOON";
            } else if (number > 46) {
                status = "LOCKED";
            } else if (number == 1) {
                status = "UNLOCKED";
            } else {
                status = "LOCKED";
            }

            levels.add(
                    new LevelResponse(
                            number,
                            "Level " + number,
                            status,
                            null
                    )
            );
        }

        return levels;
    }

    // Get all 60 levels and their current statuses.
    @Transactional
    public List<LevelResponse> getMyLevels(String username) {

        UserProgress progress = getOrCreateProgress(username);

        int currentLevel = progress.getCurrentLevel();

        List<LevelResponse> levels = new ArrayList<>();

        for (int number = 1; number <= 60; number++) {

            String status;

            if (number == 46) {
                status = "COMING_SOON";
            } else if (number > 46) {
                status = "LOCKED";
            } else if (number < currentLevel) {
                status = "COMPLETED";
            } else if (number == currentLevel) {
                status = "UNLOCKED";
            } else {
                status = "LOCKED";
            }

            levels.add(
                    new LevelResponse(
                            number,
                            "Level " + number,
                            status,
                            null
                    )
            );
        }

        return levels;
    }

    // Complete a level and award 100 points only once.
    @Transactional
    public LevelCompletionResponse completeLevel(
            String username,
            int levelNumber
    ) {

        if (levelNumber < 1 || levelNumber > 45) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Only Levels 1 to 45 can be completed currently"
            );
        }

        User user = userRepository.findByUsername(username)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "User not found"
                        )
                );

        // Lock this user's progress row while processing completion.
        UserProgress progress = progressRepository
                .findByUserIdForUpdate(user.getId())
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.CONFLICT,
                                "Progress record not found"
                        )
                );

        // Check whether this level has already been completed.
        boolean alreadyCompleted =
                completionRepository.existsByUserIdAndLevelNumber(
                        user.getId(),
                        levelNumber
                );

        if (alreadyCompleted) {
            return new LevelCompletionResponse(
                    levelNumber,
                    0L,
                    progress.getPoints(),
                    progress.getCurrentLevel(),
                    true,
                    "This level has already been completed. No additional points awarded."
            );
        }

        // Users may complete only their currently unlocked level.
        if (progress.getCurrentLevel() != levelNumber) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "You can complete only your currently unlocked level"
            );
        }

        // Save completion and update progress in the same transaction.
        LevelCompletion completion = new LevelCompletion(
                user,
                levelNumber,
                100L
        );

        completionRepository.save(completion);

        progress.setPoints(progress.getPoints() + 100L);
        progress.setCurrentLevel(levelNumber + 1);

        progressRepository.save(progress);

        return new LevelCompletionResponse(
                levelNumber,
                100L,
                progress.getPoints(),
                progress.getCurrentLevel(),
                false,
                "Level completed! 100 points awarded."
        );
    }
}
