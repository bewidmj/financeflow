package com.learning.financeflow.controller;

import com.learning.financeflow.dto.BulkCreateTransactionResponse;
import com.learning.financeflow.dto.CreateTransactionRequest;
import com.learning.financeflow.dto.CsvImportResponse;
import com.learning.financeflow.dto.TransactionResponse;
import com.learning.financeflow.service.FinancialTransactionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.learning.financeflow.entity.TransactionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import java.time.LocalDate;
import java.util.List;
import com.learning.financeflow.service.CsvTransactionImportService;


@Validated 
@RestController
@RequestMapping("/api/v1/transactions")
public class FinancialTransactionController {

    private final FinancialTransactionService service;

    private final CsvTransactionImportService csvTransactionImportService;

    public FinancialTransactionController(
            FinancialTransactionService service, CsvTransactionImportService csvTransactionImportService) {
        this.service = service;
        this.csvTransactionImportService = csvTransactionImportService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TransactionResponse create(
            @Valid @RequestBody CreateTransactionRequest request) {
        return service.create(request);
    }

    @GetMapping("/{id}")
    public ResponseEntity<TransactionResponse> findById(
            @PathVariable Long id) {
        return ResponseEntity.ok(service.findById(id));
    }


    @GetMapping
    public Page<TransactionResponse> search(
            @RequestParam(required = false) TransactionType type,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "transactionDate,desc") String sort) {

        if (page < 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Page index cannot be negative"
            );
        }

        if (size < 1 || size > 100) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Page size must be between 1 and 100"
            );
        }

        if (from != null && to != null && from.isAfter(to)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "'from' date cannot be after 'to' date"
            );
        }

        String[] sortParts = sort.split(",", -1);

        if (sortParts.length != 2
                || sortParts[0].isBlank()
                || !sortParts[1].equalsIgnoreCase("asc")
                        && !sortParts[1].equalsIgnoreCase("desc")) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Sort must use the format field,asc or field,desc"
            );
        }

        String sortField = sortParts[0].trim();

        if (!List.of("transactionDate", "amount", "category", "type", "id")
                .contains(sortField)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Unsupported sort field: " + sortField
            );
        }

        Sort.Direction direction =
                Sort.Direction.fromString(sortParts[1]);

        Pageable pageable = PageRequest.of(
                page,
                size,
                Sort.by(direction, sortField)
        );

        return service.search(type, category, from, to, pageable);
    }

    @PostMapping("/bulk")
    @ResponseStatus(HttpStatus.CREATED)
    public BulkCreateTransactionResponse createBulk(
            @RequestBody
            @Size(min = 1, max = 100,
                message = "Batch must contain between 1 and 100 transactions")
            List<@Valid CreateTransactionRequest> requests) {

        return service.createBulk(requests);
    }


    @PostMapping(
            value = "/import",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public CsvImportResponse importCsv(
            @RequestParam("file") MultipartFile file) {
        return csvTransactionImportService.importCsv(file);
    }


}