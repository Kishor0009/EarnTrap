
package com.earntrap.backend.dto;

public class LevelCompletionResponse {

    private final int levelNumber;
    private final long pointsAwarded;
    private final long totalPoints;
    private final int currentLevel;
    private final boolean alreadyCompleted;
    private final String message;

    public LevelCompletionResponse(
            int levelNumber,
            long pointsAwarded,
            long totalPoints,
            int currentLevel,
            boolean alreadyCompleted,
            String message
    ) {
        this.levelNumber = levelNumber;
        this.pointsAwarded = pointsAwarded;
        this.totalPoints = totalPoints;
        this.currentLevel = currentLevel;
        this.alreadyCompleted = alreadyCompleted;
        this.message = message;
    }

    public int getLevelNumber() {
        return levelNumber;
    }

    public long getPointsAwarded() {
        return pointsAwarded;
    }

    public long getTotalPoints() {
        return totalPoints;
    }

    public int getCurrentLevel() {
        return currentLevel;
    }

    public boolean isAlreadyCompleted() {
        return alreadyCompleted;
    }

    public String getMessage() {
        return message;
    }
}
