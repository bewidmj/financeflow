package com.learning.financeflow.service;

import com.learning.financeflow.dto.BulkCreateTransactionResponse;
import com.learning.financeflow.dto.CreateTransactionRequest;
import com.learning.financeflow.dto.TransactionResponse;
import com.learning.financeflow.entity.FinancialTransaction;
import com.learning.financeflow.entity.TransactionType;
import com.learning.financeflow.repository.FinancialTransactionRepository;
import com.learning.financeflow.specification.TransactionSpecifications;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import org.springframework.data.domain.Pageable;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
public class FinancialTransactionService {

    private final FinancialTransactionRepository repository;

    public FinancialTransactionService(
            FinancialTransactionRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public TransactionResponse create(
            CreateTransactionRequest request,
            UUID ownerId) {

        FinancialTransaction transaction =
                new FinancialTransaction(
                        ownerId,
                        request.amount(),
                        request.type(),
                        request.category(),
                        request.description(),
                        request.transactionDate());

        FinancialTransaction saved = repository.save(transaction);

        return toResponse(saved);
    }

    private TransactionResponse toResponse(
            FinancialTransaction transaction) {
        return new TransactionResponse(
                transaction.getId(),
                transaction.getAmount(),
                transaction.getType(),
                transaction.getCategory(),
                transaction.getDescription(),
                transaction.getTransactionDate());
    }

    @Transactional(readOnly = true)
    public TransactionResponse findById(Long id, UUID ownerId) {
        FinancialTransaction transaction = repository.findByIdAndOwnerId(id, ownerId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Transaction not found with id: " + id
                ));

        return toResponse(transaction);
    }

    @Transactional
    public TransactionResponse update(
            Long id,
            CreateTransactionRequest request,
            UUID ownerId) {
        FinancialTransaction transaction = repository.findByIdAndOwnerId(id, ownerId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Transaction not found with id: " + id));
        transaction.update(
                request.amount(),
                request.type(),
                request.category(),
                request.description(),
                request.transactionDate());
        return toResponse(transaction);
    }

    @Transactional
    public void delete(Long id, UUID ownerId) {
        FinancialTransaction transaction = repository.findByIdAndOwnerId(id, ownerId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Transaction not found with id: " + id));
        repository.delete(transaction);
    }

    @Transactional(readOnly = true)
    public Page<TransactionResponse> searchForAdmin(
            UUID ownerId,
            TransactionType type,
            String category,
            LocalDate from,
            LocalDate to,
            Pageable pageable) {
        Specification<FinancialTransaction> specification =
                Specification.where(TransactionSpecifications.hasOwnerId(ownerId))
                        .and(TransactionSpecifications.hasType(type))
                        .and(TransactionSpecifications.hasCategory(category))
                        .and(TransactionSpecifications.dateOnOrAfter(from))
                        .and(TransactionSpecifications.dateOnOrBefore(to));
        return repository.findAll(specification, pageable).map(this::toResponse);
    }


    @Transactional(readOnly = true)
    public Page<TransactionResponse> search(
            TransactionType type,
            String category,
            LocalDate from,
            LocalDate to,
            Pageable pageable,
            UUID ownerId) {

        Specification<FinancialTransaction> specification =
                Specification
                        .where(TransactionSpecifications.hasOwnerId(ownerId))
                        .and(TransactionSpecifications.hasType(type))
                        .and(TransactionSpecifications.hasCategory(category))
                        .and(TransactionSpecifications.dateOnOrAfter(from))
                        .and(TransactionSpecifications.dateOnOrBefore(to));

        return repository.findAll(specification, pageable)
                .map(this::toResponse);
    }

    @Transactional
    public BulkCreateTransactionResponse createBulk(
            List<CreateTransactionRequest> requests,
            UUID ownerId) {

        List<FinancialTransaction> transactions = requests.stream()
                .map(request -> new FinancialTransaction(
                        ownerId,
                        request.amount(),
                        request.type(),
                        request.category(),
                        request.description(),
                        request.transactionDate()
                ))
                .toList();

        List<FinancialTransaction> savedTransactions =
                repository.saveAll(transactions);

        List<TransactionResponse> responses = savedTransactions.stream()
                .map(this::toResponse)
                .toList();

        return new BulkCreateTransactionResponse(
                responses.size(),
                responses
        );
    }


}