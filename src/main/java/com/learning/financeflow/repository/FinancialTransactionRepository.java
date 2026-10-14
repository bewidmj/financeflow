
package com.learning.financeflow.repository;

import com.learning.financeflow.entity.FinancialTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface FinancialTransactionRepository
        extends JpaRepository<FinancialTransaction, Long>,
                JpaSpecificationExecutor<FinancialTransaction> {
}
