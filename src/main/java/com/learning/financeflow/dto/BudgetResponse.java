package com.learning.financeflow.dto;

import java.math.BigDecimal;

public record BudgetResponse(
    Long id,
    String category,
    String month,
    BigDecimal budgetAmount,
    BigDecimal spentAmount,
    BigDecimal remainingAmount,
    BigDecimal percentageUsed
) {}
