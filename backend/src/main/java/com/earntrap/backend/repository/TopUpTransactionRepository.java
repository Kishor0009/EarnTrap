package com.earntrap.backend.repository;

import com.earntrap.backend.entity.TopUpTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TopUpTransactionRepository extends JpaRepository<TopUpTransaction, Long> {

    Optional<TopUpTransaction> findByOrderReference(String orderReference);

    Optional<TopUpTransaction> findByProviderOrderId(String providerOrderId);

    List<TopUpTransaction> findByUserIdOrderByCreatedAtDesc(Long userId);

    @Query(
            value = "SELECT * FROM top_up_transactions WHERE id = :id FOR UPDATE",
            nativeQuery = true
    )
    Optional<TopUpTransaction> findByIdForUpdate(@Param("id") Long id);
}
