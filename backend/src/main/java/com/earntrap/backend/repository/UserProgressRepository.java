package com.earntrap.backend.repository;

import com.earntrap.backend.entity.UserProgress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserProgressRepository
        extends JpaRepository<UserProgress, Long> {

    Optional<UserProgress> findByUserId(Long userId);

    @Query(
            value = "SELECT * FROM user_progress WHERE user_id = :userId FOR UPDATE",
            nativeQuery = true
    )
    Optional<UserProgress> findByUserIdForUpdate(
            @Param("userId") Long userId
    );
}