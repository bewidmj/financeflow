package com.learning.financeflow.controller;

import com.learning.financeflow.dto.BudgetResponse;
import com.learning.financeflow.dto.CreateBudgetRequest;
import com.learning.financeflow.dto.UpdateBudgetRequest;
import com.learning.financeflow.service.BudgetService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/budgets")
public class BudgetController {
    private final BudgetService budgetService;

    public BudgetController(BudgetService budgetService) {
        this.budgetService = budgetService;
    }

    @PostMapping
    public ResponseEntity<BudgetResponse> create(@Valid @RequestBody CreateBudgetRequest request) {
        BudgetResponse created = budgetService.create(request);
        return ResponseEntity.created(URI.create("/api/v1/budgets/" + created.id())).body(created);
    }

    @GetMapping
    public List<BudgetResponse> findByMonth(@RequestParam String month) {
        return budgetService.findByMonth(month);
    }

    @GetMapping("/{id}")
    public BudgetResponse findById(
        @PathVariable Long id,
        @RequestParam(required = false) String month
    ) {
        return budgetService.findById(id, month);
    }

    @PutMapping("/{id}")
    public BudgetResponse update(@PathVariable Long id, @Valid @RequestBody UpdateBudgetRequest request) {
        return budgetService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        budgetService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
