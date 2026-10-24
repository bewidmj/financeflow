package com.learning.financeflow.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(
    name = "budgets",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_budget_category_month",
        columnNames = {"category", "month_start"}
    )
)
public class Budget {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String category;

    @Column(name = "month_start", nullable = false)
    private LocalDate monthStart;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    protected Budget() {}

    public Budget(String category, LocalDate monthStart, BigDecimal amount) {
        this.category = category;
        this.monthStart = monthStart;
        this.amount = amount;
    }

    public Long getId() { return id; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public LocalDate getMonthStart() { return monthStart; }
    public void setMonthStart(LocalDate monthStart) { this.monthStart = monthStart; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
}
