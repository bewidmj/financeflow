package com.learning.financeflow.dto;

import com.learning.financeflow.entity.TransactionType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record TransactionResponse(
        Long id,
        BigDecimal amount,
        TransactionType type,
        String category,
        String description,
        LocalDate transactionDate,
        UUID accountId,
        long transactionVersion
) {
    public TransactionResponse(
            Long id,
            BigDecimal amount,
            TransactionType type,
            String category,
            String description,
            LocalDate transactionDate) {
        this(id, amount, type, category, description, transactionDate, null, 1);
    }
}