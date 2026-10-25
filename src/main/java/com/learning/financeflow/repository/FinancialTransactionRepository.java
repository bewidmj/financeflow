
package com.learning.financeflow.repository;

import com.learning.financeflow.entity.FinancialTransaction;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface FinancialTransactionRepository
        extends JpaRepository<FinancialTransaction, Long>,
                JpaSpecificationExecutor<FinancialTransaction> {
    Optional<FinancialTransaction> findByIdAndOwnerId(Long id, UUID ownerId);
}
