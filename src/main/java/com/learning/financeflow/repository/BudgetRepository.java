package com.learning.financeflow.repository;

import com.learning.financeflow.entity.Budget;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BudgetRepository extends JpaRepository<Budget, Long> {
    List<Budget> findByMonthStartOrderByCategoryAsc(LocalDate monthStart);
    Optional<Budget> findByIdAndMonthStart(Long id, LocalDate monthStart);
    boolean existsByCategoryIgnoreCaseAndMonthStart(String category, LocalDate monthStart);
    boolean existsByCategoryIgnoreCaseAndMonthStartAndIdNot(
        String category, LocalDate monthStart, Long id
    );
}
