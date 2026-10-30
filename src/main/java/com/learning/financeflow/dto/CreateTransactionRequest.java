package com.learning.financeflow.dto;

import com.learning.financeflow.entity.TransactionType;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CreateTransactionRequest(

        @NotNull
        @DecimalMin(value = "0.01")
        @Digits(integer = 17, fraction = 2)
        BigDecimal amount,

        @NotNull
        TransactionType type,

        @NotBlank
        @Size(max = 100)
        String category,

        @Size(max = 500)
        String description,

        @NotNull
        @PastOrPresent
        LocalDate transactionDate,

        UUID accountId

) {
    public CreateTransactionRequest(
            BigDecimal amount,
            TransactionType type,
            String category,
            String description,
            LocalDate transactionDate) {
        this(amount, type, category, description, transactionDate, null);
    }
}