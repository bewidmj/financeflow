package com.learning.financeflow.service;

import com.learning.financeflow.dto.BulkCreateTransactionResponse;
import com.learning.financeflow.dto.CreateTransactionRequest;
import com.learning.financeflow.dto.TransactionResponse;
import com.learning.financeflow.entity.FinancialTransaction;
import com.learning.financeflow.entity.TransactionType;
import com.learning.financeflow.repository.FinancialTransactionRepository;
import com.learning.financeflow.event.TransactionSnapshot;
import com.learning.financeflow.outbox.OutboxEventFactory;
import com.learning.financeflow.outbox.OutboxEventRepository;
import com.learning.financeflow.repository.CsvImportRowStore;
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
    private final OutboxEventRepository outboxRepository;
    private final OutboxEventFactory outboxEventFactory;
    private final CsvImportRowStore csvImportRowStore;

    public FinancialTransactionService(
            FinancialTransactionRepository repository,
            OutboxEventRepository outboxRepository,
            OutboxEventFactory outboxEventFactory,
            CsvImportRowStore csvImportRowStore) {
        this.repository = repository;
        this.outboxRepository = outboxRepository;
        this.outboxEventFactory = outboxEventFactory;
        this.csvImportRowStore = csvImportRowStore;
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
                        request.transactionDate(),
                        request.accountId());

        FinancialTransaction saved = repository.save(transaction);
        outboxRepository.save(outboxEventFactory.created(saved));

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
                transaction.getTransactionDate(),
                transaction.getAccountId(),
                transaction.getTransactionVersion());
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
        TransactionSnapshot before = TransactionSnapshot.from(transaction);
        transaction.update(
                request.amount(),
                request.type(),
                request.category(),
                request.description(),
                request.transactionDate(),
                request.accountId());
        outboxRepository.save(outboxEventFactory.updated(transaction, before));
        return toResponse(transaction);
    }

    @Transactional
    public void delete(Long id, UUID ownerId) {
        FinancialTransaction transaction = repository.findByIdAndOwnerId(id, ownerId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Transaction not found with id: " + id));
        outboxRepository.save(outboxEventFactory.deleted(
                transaction, TransactionSnapshot.from(transaction)));
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
                        request.transactionDate(),
                        request.accountId()
                ))
                .toList();

        List<FinancialTransaction> savedTransactions =
                repository.saveAll(transactions);
        outboxRepository.saveAll(savedTransactions.stream()
                .map(outboxEventFactory::created)
                .toList());

        List<TransactionResponse> responses = savedTransactions.stream()
                .map(this::toResponse)
                .toList();

        return new BulkCreateTransactionResponse(
                responses.size(),
                responses
        );
    }

    @Transactional
    public int createImportedBulk(
            List<CsvTransactionRow> rows, UUID ownerId, String fileHash) {
        List<FinancialTransaction> transactions = new java.util.ArrayList<>();
        List<CsvTransactionRow> reservedRows = new java.util.ArrayList<>();
        for (CsvTransactionRow row : rows) {
            if (!csvImportRowStore.reserve(ownerId, fileHash, row.rowNumber())) {
                continue;
            }
            CreateTransactionRequest request = row.request();
            transactions.add(new FinancialTransaction(
                    ownerId,
                    request.amount(),
                    request.type(),
                    request.category(),
                    request.description(),
                    request.transactionDate(),
                    request.accountId()));
            reservedRows.add(row);
        }
        if (transactions.isEmpty()) {
            return 0;
        }
        List<FinancialTransaction> saved = repository.saveAll(transactions);
        outboxRepository.saveAll(saved.stream()
                .map(outboxEventFactory::created)
                .toList());
        for (int index = 0; index < saved.size(); index++) {
            csvImportRowStore.complete(
                    ownerId,
                    fileHash,
                    reservedRows.get(index).rowNumber(),
                    saved.get(index).getId());
        }
        return saved.size();
    }

}