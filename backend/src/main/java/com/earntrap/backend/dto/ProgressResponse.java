package com.earntrap.backend.dto;

public class ProgressResponse {

    private final int currentLevel;
    private final Long points;
    private final boolean turboPassActive;

    public ProgressResponse(
            int currentLevel,
            Long points,
            boolean turboPassActive
    ) {
        this.currentLevel = currentLevel;
        this.points = points;
        this.turboPassActive = turboPassActive;
    }

    public int getCurrentLevel() {
        return currentLevel;
    }

    public Long getPoints() {
        return points;
    }

    public boolean isTurboPassActive() {
        return turboPassActive;
    }
}
