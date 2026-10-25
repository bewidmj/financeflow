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
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class BudgetService {
    private final BudgetRepository budgetRepository;
    private final BudgetTransactionQueries transactionQueries;

    public BudgetService(BudgetRepository budgetRepository, BudgetTransactionQueries transactionQueries) {
        this.budgetRepository = budgetRepository;
        this.transactionQueries = transactionQueries;
    }

    public BudgetResponse create(CreateBudgetRequest request, UUID ownerId) {
        YearMonth month = parseMonth(request.month());
        if (budgetRepository.existsByOwnerIdAndCategoryIgnoreCaseAndMonthStart(
                ownerId, request.category().trim(), month.atDay(1))) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "A budget already exists for this category and month");
        }
        Budget budget = new Budget(
            ownerId, request.category().trim(), month.atDay(1), request.amount());
        return toResponse(budgetRepository.save(budget));
    }

    @Transactional(readOnly = true)
    public List<BudgetResponse> findByMonth(String monthText, UUID ownerId) {
        LocalDate monthStart = parseMonth(monthText).atDay(1);
        return budgetRepository.findByOwnerIdAndMonthStartOrderByCategoryAsc(
                ownerId, monthStart)
            .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public BudgetResponse findById(Long id, String monthText, UUID ownerId) {
        Budget budget = monthText == null
            ? findOwnedBudget(id, ownerId)
            : budgetRepository.findByIdAndOwnerIdAndMonthStart(
                id, ownerId, parseMonth(monthText).atDay(1))
                .orElseThrow(() -> budgetNotFound(id));
        return toResponse(budget);
    }

    public BudgetResponse update(
            Long id, UpdateBudgetRequest request, UUID ownerId) {
        Budget budget = findOwnedBudget(id, ownerId);
        LocalDate monthStart = parseMonth(request.month()).atDay(1);
        if (budgetRepository.existsByOwnerIdAndCategoryIgnoreCaseAndMonthStartAndIdNot(
                ownerId, request.category().trim(), monthStart, id)) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "A budget already exists for this category and month");
        }
        budget.setCategory(request.category().trim());
        budget.setMonthStart(monthStart);
        budget.setAmount(request.amount());
        return toResponse(budgetRepository.save(budget));
    }

    public void delete(Long id, UUID ownerId) {
        Budget budget = findOwnedBudget(id, ownerId);
        budgetRepository.delete(budget);
    }

    private Budget findOwnedBudget(Long id, UUID ownerId) {
        return budgetRepository.findByIdAndOwnerId(id, ownerId)
            .orElseThrow(() -> budgetNotFound(id));
    }

    private ResponseStatusException budgetNotFound(Long id) {
        return new ResponseStatusException(
            HttpStatus.NOT_FOUND, "Budget not found: " + id);
    }

    private BudgetResponse toResponse(Budget budget) {
        LocalDate start = budget.getMonthStart();
        BigDecimal spent = transactionQueries.sumExpensesForCategoryAndDateRange(
            budget.getCategory(), start, start.plusMonths(1),
            TransactionType.EXPENSE, budget.getOwnerId());
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
