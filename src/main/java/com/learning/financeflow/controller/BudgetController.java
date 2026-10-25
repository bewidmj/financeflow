package com.learning.financeflow.controller;

import com.learning.financeflow.dto.BudgetResponse;
import com.learning.financeflow.dto.CreateBudgetRequest;
import com.learning.financeflow.dto.UpdateBudgetRequest;
import com.learning.financeflow.service.BudgetService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/budgets")
public class BudgetController {
    private final BudgetService budgetService;

    public BudgetController(BudgetService budgetService) {
        this.budgetService = budgetService;
    }

    @PostMapping
    public ResponseEntity<BudgetResponse> create(
        @Valid @RequestBody CreateBudgetRequest request,
        @AuthenticationPrincipal Jwt jwt
    ) {
        BudgetResponse created = budgetService.create(request, ownerId(jwt));
        return ResponseEntity.created(URI.create("/api/v1/budgets/" + created.id())).body(created);
    }

    @GetMapping
    public List<BudgetResponse> findByMonth(
        @RequestParam String month,
        @AuthenticationPrincipal Jwt jwt
    ) {
        return budgetService.findByMonth(month, ownerId(jwt));
    }

    @GetMapping("/{id}")
    public BudgetResponse findById(
        @PathVariable Long id,
        @RequestParam(required = false) String month,
        @AuthenticationPrincipal Jwt jwt
    ) {
        return budgetService.findById(id, month, ownerId(jwt));
    }

    @PutMapping("/{id}")
    public BudgetResponse update(
        @PathVariable Long id,
        @Valid @RequestBody UpdateBudgetRequest request,
        @AuthenticationPrincipal Jwt jwt
    ) {
        return budgetService.update(id, request, ownerId(jwt));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
        @PathVariable Long id,
        @AuthenticationPrincipal Jwt jwt
    ) {
        budgetService.delete(id, ownerId(jwt));
        return ResponseEntity.noContent().build();
    }

    private UUID ownerId(Jwt jwt) {
        if (jwt == null || jwt.getSubject() == null) {
            throw new ResponseStatusException(
                HttpStatus.UNAUTHORIZED, "Invalid token subject");
        }
        try {
            return UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(
                HttpStatus.UNAUTHORIZED, "Invalid token subject", ex);
        }
    }
}
