package com.learning.financeflow.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(
    name = "financial_transactions",
    indexes = @Index(name = "idx_financial_transactions_owner_id", columnList = "owner_id")
)
public class FinancialTransaction {

    @Id
    @GeneratedValue(
        strategy = GenerationType.SEQUENCE,
        generator = "transaction_seq_generator"
    )
    @SequenceGenerator(
        name = "transaction_seq_generator",
        sequenceName = "financial_transaction_seq",
        allocationSize = 50
    )
    private Long id;

    @Column(name = "owner_id")
    private UUID ownerId;

    @Column(name = "account_id")
    private UUID accountId;

    @Column(name = "transaction_version", nullable = false)
    private long transactionVersion = 1;

    @Version
    @Column(name = "persistence_version", nullable = false)
    private long persistenceVersion;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TransactionType type;

    @Column(nullable = false, length = 100)
    private String category;

    @Column(length = 500)
    private String description;

    @Column(nullable = false)
    private LocalDate transactionDate;

    protected FinancialTransaction() {
        // Required by JPA
    }

    public FinancialTransaction(
            UUID ownerId,
            BigDecimal amount,
            TransactionType type,
            String category,
            String description,
            LocalDate transactionDate,
            UUID accountId) {
        this.ownerId = ownerId;
        this.accountId = accountId;
        this.amount = amount;
        this.type = type;
        this.category = category;
        this.description = description;
        this.transactionDate = transactionDate;
    }

    public FinancialTransaction(
            UUID ownerId,
            BigDecimal amount,
            TransactionType type,
            String category,
            String description,
            LocalDate transactionDate) {
        this(ownerId, amount, type, category, description, transactionDate, null);
    }

    public Long getId() {
        return id;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public long getTransactionVersion() {
        return transactionVersion;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public TransactionType getType() {
        return type;
    }

    public String getCategory() {
        return category;
    }

    public String getDescription() {
        return description;
    }

    public LocalDate getTransactionDate() {
        return transactionDate;
    }

    public void update(
            BigDecimal amount,
            TransactionType type,
            String category,
            String description,
            LocalDate transactionDate,
            UUID accountId) {
        this.amount = amount;
        this.type = type;
        this.category = category;
        this.description = description;
        this.transactionDate = transactionDate;
        if (accountId != null) {
            this.accountId = accountId;
        }
        transactionVersion++;
    }
}
