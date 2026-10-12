package com.learning.financeflow.controller;

import com.learning.financeflow.entity.FinancialTransaction;
import com.learning.financeflow.service.FinancialTransactionService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/transactions")
public class FinancialTransactionController {

    private final FinancialTransactionService service;

    public FinancialTransactionController(
            FinancialTransactionService service) {
        this.service = service;
    }

    @PostMapping
    public FinancialTransaction create(
            @RequestBody FinancialTransaction transaction) {
        return service.create(transaction);
    }

    @GetMapping
    public List<FinancialTransaction> findAll() {
        return service.findAll();
    }
}