package com.learning.financeflow.repository;

import com.learning.financeflow.entity.FinancialTransaction;
import com.learning.financeflow.entity.TransactionType;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Integration note: FinanceFlow already has FinancialTransactionRepository.
 * Move this query method into that existing repository rather than registering
 * two repositories for FinancialTransaction, then remove this interface.
 */
public interface BudgetTransactionQueries extends JpaRepository<FinancialTransaction, Long> {
    @Query("select coalesce(sum(t.amount), 0) " +
           "from FinancialTransaction t " +
           "where lower(t.category) = lower(:category) " +
           "and t.transactionDate >= :startDate " +
           "and t.transactionDate < :endDateExclusive " +
           "and t.type = :type")
    BigDecimal sumExpensesForCategoryAndDateRange(
        @Param("category") String category,
        @Param("startDate") LocalDate startDate,
        @Param("endDateExclusive") LocalDate endDateExclusive,
        @Param("type") TransactionType type
    );
}
