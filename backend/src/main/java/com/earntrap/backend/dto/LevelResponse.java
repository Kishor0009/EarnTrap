
package com.earntrap.backend.dto;

public class LevelResponse {

    private final int levelNumber;
    private final String title;
    private final String status;
    private final String videoUrl;

    public LevelResponse(
            int levelNumber,
            String title,
            String status,
            String videoUrl
    ) {
        this.levelNumber = levelNumber;
        this.title = title;
        this.status = status;
        this.videoUrl = videoUrl;
    }

    public int getLevelNumber() {
        return levelNumber;
    }

    public String getTitle() {
        return title;
    }

    public String getStatus() {
        return status;
    }

    public String getVideoUrl() {
        return videoUrl;
    }
}
