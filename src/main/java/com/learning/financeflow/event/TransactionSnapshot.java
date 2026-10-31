package com.learning.financeflow.event;

import com.learning.financeflow.entity.FinancialTransaction;
import com.learning.financeflow.entity.TransactionType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record TransactionSnapshot(
        UUID accountId,
        UUID creditCardId,
        TransactionType transactionType,
        BigDecimal amount,
        String currency,
        String category,
        String description,
        LocalDate transactionDate) {

    public static TransactionSnapshot from(FinancialTransaction transaction) {
        return new TransactionSnapshot(
                transaction.getAccountId(),
                transaction.getCreditCardId(),
                transaction.getType(),
                transaction.getAmount(),
                "INR",
                transaction.getCategory(),
                transaction.getDescription(),
                transaction.getTransactionDate());
    }
}
