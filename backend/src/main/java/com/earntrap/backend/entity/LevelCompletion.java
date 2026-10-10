
package com.earntrap.backend.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "level_completions",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_user_level_completion",
                        columnNames = {"user_id", "level_number"}
                )
        }
)
public class LevelCompletion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "level_number", nullable = false)
    private Integer levelNumber;

    @Column(nullable = false)
    private Long pointsAwarded;

    @Column(nullable = false, updatable = false)
    private LocalDateTime completedAt;

    protected LevelCompletion() {
    }

    public LevelCompletion(
            User user,
            Integer levelNumber,
            Long pointsAwarded
    ) {
        this.user = user;
        this.levelNumber = levelNumber;
        this.pointsAwarded = pointsAwarded;
    }

    @PrePersist
    protected void onCreate() {
        completedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public Integer getLevelNumber() {
        return levelNumber;
    }

    public Long getPointsAwarded() {
        return pointsAwarded;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }
}
