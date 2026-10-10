
package com.earntrap.backend.repository;

import com.earntrap.backend.entity.LevelCompletion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LevelCompletionRepository
        extends JpaRepository<LevelCompletion, Long> {

    boolean existsByUserIdAndLevelNumber(
            Long userId,
            Integer levelNumber
    );

    @Query(
            value = "SELECT COALESCE(SUM(points_awarded), 0) FROM level_completions WHERE user_id = :userId",
            nativeQuery = true
    )
    Long sumPointsByUserId(@Param("userId") Long userId);
}
