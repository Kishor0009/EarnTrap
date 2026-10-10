package com.earntrap.backend.repository;

import com.earntrap.backend.entity.UserWallet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserWalletRepository extends JpaRepository<UserWallet, Long> {

    Optional<UserWallet> findByUserId(Long userId);

    @Query(
            value = "SELECT * FROM user_wallets WHERE user_id = :userId FOR UPDATE",
            nativeQuery = true
    )
    Optional<UserWallet> findByUserIdForUpdate(@Param("userId") Long userId);
}
