package com.earntrap.backend.repository;

import com.earntrap.backend.entity.Redemption;
import com.earntrap.backend.enums.RedemptionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RedemptionRepository
        extends JpaRepository<Redemption, Long> {

    List<Redemption> findByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<Redemption> findByReferenceCode(String referenceCode);

    @Query("SELECT COALESCE(SUM(r.amountInRupees), 0L) FROM Redemption r WHERE r.user.id = :userId AND r.status = :status")
    Long sumAmountByUserIdAndStatus(
            @Param("userId") Long userId,
            @Param("status") RedemptionStatus status
    );
}
