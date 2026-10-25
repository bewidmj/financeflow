package com.learning.financeflow.repository;

import com.learning.financeflow.entity.Budget;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BudgetRepository extends JpaRepository<Budget, Long> {
    List<Budget> findByOwnerIdAndMonthStartOrderByCategoryAsc(
        UUID ownerId, LocalDate monthStart
    );
    Optional<Budget> findByIdAndOwnerId(Long id, UUID ownerId);
    Optional<Budget> findByIdAndOwnerIdAndMonthStart(
        Long id, UUID ownerId, LocalDate monthStart
    );
    boolean existsByOwnerIdAndCategoryIgnoreCaseAndMonthStart(
        UUID ownerId, String category, LocalDate monthStart
    );
    boolean existsByOwnerIdAndCategoryIgnoreCaseAndMonthStartAndIdNot(
        UUID ownerId, String category, LocalDate monthStart, Long id
    );
}
