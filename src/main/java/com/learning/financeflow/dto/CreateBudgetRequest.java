package com.learning.financeflow.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;

public record CreateBudgetRequest(
    @NotBlank String category,
    @NotBlank @Pattern(regexp = "\\d{4}-\\d{2}", message = "month must use YYYY-MM format")
    String month,
    @NotNull @DecimalMin(value = "0.01", message = "amount must be greater than zero")
    BigDecimal amount
) {}
