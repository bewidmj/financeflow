package com.learning.financeflow.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(
    name = "budgets",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_budget_owner_category_month",
        columnNames = {"owner_id", "category", "month_start"}
    ),
    indexes = @Index(name = "idx_budgets_owner_id", columnList = "owner_id")
)
public class Budget {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_id")
    private UUID ownerId;

    @Column(nullable = false, length = 100)
    private String category;

    @Column(name = "month_start", nullable = false)
    private LocalDate monthStart;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    protected Budget() {}

    public Budget(UUID ownerId, String category, LocalDate monthStart, BigDecimal amount) {
        this.ownerId = ownerId;
        this.category = category;
        this.monthStart = monthStart;
        this.amount = amount;
    }

    public Long getId() { return id; }
    public UUID getOwnerId() { return ownerId; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public LocalDate getMonthStart() { return monthStart; }
    public void setMonthStart(LocalDate monthStart) { this.monthStart = monthStart; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
}
