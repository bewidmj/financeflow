package com.learning.financeflow.controller;

import com.learning.financeflow.dto.TransactionResponse;
import com.learning.financeflow.entity.TransactionType;
import com.learning.financeflow.service.FinancialTransactionService;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/admin/users/{ownerId}/transactions")
@PreAuthorize("hasRole('ADMIN')")
public class AdminTransactionController {

    private final FinancialTransactionService service;

    public AdminTransactionController(FinancialTransactionService service) {
        this.service = service;
    }

    @GetMapping
    public Page<TransactionResponse> search(
            @PathVariable UUID ownerId,
            @RequestParam(required = false) TransactionType type,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "transactionDate,desc") String sort) {
        if (page < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Page index cannot be negative");
        }
        if (size < 1 || size > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Page size must be between 1 and 100");
        }
        if (from != null && to != null && from.isAfter(to)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "'from' date cannot be after 'to' date");
        }
        String[] sortParts = sort.split(",", -1);
        if (sortParts.length != 2
                || sortParts[0].isBlank()
                || !sortParts[1].equalsIgnoreCase("asc")
                        && !sortParts[1].equalsIgnoreCase("desc")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Sort must use the format field,asc or field,desc");
        }
        String sortField = sortParts[0].trim();
        if (!List.of("transactionDate", "amount", "category", "type", "id")
                .contains(sortField)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Unsupported sort field: " + sortField);
        }
        Pageable pageable = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Direction.fromString(sortParts[1]), sortField));
        return service.searchForAdmin(ownerId, type, category, from, to, pageable);
    }
}
