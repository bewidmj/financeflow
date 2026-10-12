package com.learning.financeflow.service;

import com.learning.financeflow.entity.FinancialTransaction;
import com.learning.financeflow.repository.FinancialTransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class FinancialTransactionService {

    private final FinancialTransactionRepository repository;

    public FinancialTransactionService(
            FinancialTransactionRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public FinancialTransaction create(
            FinancialTransaction transaction) {
        return repository.save(transaction);
    }

    @Transactional(readOnly = true)
    public List<FinancialTransaction> findAll() {
        return repository.findAll();
    }
}