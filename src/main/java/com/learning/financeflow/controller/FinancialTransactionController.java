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
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;

import com.learning.financeflow.entity.TransactionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
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
            @Valid @RequestBody CreateTransactionRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return service.create(request, ownerId(jwt));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TransactionResponse> findById(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(service.findById(id, ownerId(jwt)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<TransactionResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody CreateTransactionRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(service.update(id, request, ownerId(jwt)));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt) {
        service.delete(id, ownerId(jwt));
    }


    @GetMapping
    public Page<TransactionResponse> search(
            @RequestParam(required = false) TransactionType type,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "transactionDate,desc") String sort,
            @AuthenticationPrincipal Jwt jwt) {

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

        return service.search(type, category, from, to, pageable, ownerId(jwt));
    }


    @PostMapping("/bulk")
    @ResponseStatus(HttpStatus.CREATED)
    public BulkCreateTransactionResponse createBulk(
            @RequestBody
            @Size(min = 1, max = 100,
                message = "Batch must contain between 1 and 100 transactions")
            List<@Valid CreateTransactionRequest> requests,
            @AuthenticationPrincipal Jwt jwt) {

        return service.createBulk(requests, ownerId(jwt));
    }


    @PostMapping(
            value = "/import",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public CsvImportResponse importCsv(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal Jwt jwt) {
        return csvTransactionImportService.importCsv(file, ownerId(jwt));
    }

    private UUID ownerId(Jwt jwt) {
        if (jwt == null || jwt.getSubject() == null) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Invalid token subject");
        }
        try {
            return UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Invalid token subject",
                    ex);
        }
    }

}