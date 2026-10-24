package com.learning.financeflow.service;

import com.learning.financeflow.dto.BudgetResponse;
import com.learning.financeflow.dto.CreateBudgetRequest;
import com.learning.financeflow.dto.UpdateBudgetRequest;
import com.learning.financeflow.entity.Budget;
import com.learning.financeflow.entity.TransactionType;
import com.learning.financeflow.repository.BudgetRepository;
import com.learning.financeflow.repository.BudgetTransactionQueries;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class BudgetService {
    private final BudgetRepository budgetRepository;
    private final BudgetTransactionQueries transactionQueries;

    public BudgetService(BudgetRepository budgetRepository, BudgetTransactionQueries transactionQueries) {
        this.budgetRepository = budgetRepository;
        this.transactionQueries = transactionQueries;
    }

    public BudgetResponse create(CreateBudgetRequest request) {
        YearMonth month = parseMonth(request.month());
        if (budgetRepository.existsByCategoryIgnoreCaseAndMonthStart(request.category().trim(), month.atDay(1))) {
            throw new IllegalArgumentException("A budget already exists for this category and month");
        }
        Budget budget = new Budget(request.category().trim(), month.atDay(1), request.amount());
        return toResponse(budgetRepository.save(budget));
    }

    @Transactional(readOnly = true)
    public List<BudgetResponse> findByMonth(String monthText) {
        LocalDate monthStart = parseMonth(monthText).atDay(1);
        return budgetRepository.findByMonthStartOrderByCategoryAsc(monthStart)
            .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public BudgetResponse findById(Long id, String monthText) {
        Budget budget = budgetRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Budget not found: " + id));
        if (monthText != null && !budget.getMonthStart().equals(parseMonth(monthText).atDay(1))) {
            throw new IllegalArgumentException("Budget not found for the requested month");
        }
        return toResponse(budget);
    }

    public BudgetResponse update(Long id, UpdateBudgetRequest request) {
        Budget budget = budgetRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Budget not found: " + id));
        LocalDate monthStart = parseMonth(request.month()).atDay(1);
        if (budgetRepository.existsByCategoryIgnoreCaseAndMonthStartAndIdNot(
            request.category().trim(), monthStart, id)) {
            throw new IllegalArgumentException("A budget already exists for this category and month");
        }
        budget.setCategory(request.category().trim());
        budget.setMonthStart(monthStart);
        budget.setAmount(request.amount());
        return toResponse(budgetRepository.save(budget));
    }

    public void delete(Long id) {
        Budget budget = budgetRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Budget not found: " + id));
        budgetRepository.delete(budget);
    }

    private BudgetResponse toResponse(Budget budget) {
        LocalDate start = budget.getMonthStart();
        BigDecimal spent = transactionQueries.sumExpensesForCategoryAndDateRange(
            budget.getCategory(), start, start.plusMonths(1), TransactionType.EXPENSE);
        if (spent == null) spent = BigDecimal.ZERO;
        BigDecimal remaining = budget.getAmount().subtract(spent);
        BigDecimal percentage = spent.multiply(BigDecimal.valueOf(100))
            .divide(budget.getAmount(), 2, RoundingMode.HALF_UP);
        return new BudgetResponse(
            budget.getId(), budget.getCategory(), YearMonth.from(start).toString(),
            budget.getAmount(), spent, remaining, percentage);
    }

    private YearMonth parseMonth(String monthText) {
        try {
            return YearMonth.parse(monthText);
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("month must use YYYY-MM format", ex);
        }
    }
}
