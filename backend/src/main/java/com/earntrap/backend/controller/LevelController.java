package com.earntrap.backend.controller;

import com.earntrap.backend.dto.LevelCompletionResponse;
import com.earntrap.backend.dto.LevelResponse;
import com.earntrap.backend.dto.ProgressResponse;
import com.earntrap.backend.service.LevelService;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class LevelController {

    private final LevelService levelService;

    public LevelController(LevelService levelService) {
        this.levelService = levelService;
    }

    // Get all 60 levels for the authenticated user
    @GetMapping("/levels")
    public List<LevelResponse> getMyLevels(
            Authentication authentication
    ) {
        return levelService.getMyLevels(
                authentication.getName()
        );
    }

    // Get the authenticated user's progress
    @GetMapping("/progress/me")
    public ProgressResponse getMyProgress(
            Authentication authentication
    ) {
        return levelService.getMyProgress(
                authentication.getName()
        );
    }

    // Complete the currently unlocked level and award points
    @PostMapping("/levels/{levelNumber}/complete")
    public LevelCompletionResponse completeLevel(
            @PathVariable int levelNumber,
            Authentication authentication
    ) {
        return levelService.completeLevel(
                authentication.getName(),
                levelNumber
        );
    }
}