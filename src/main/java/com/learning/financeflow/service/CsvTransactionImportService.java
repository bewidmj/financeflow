
package com.learning.financeflow.service;

import com.learning.financeflow.dto.CreateTransactionRequest;
import com.learning.financeflow.dto.CsvImportResponse;
import com.learning.financeflow.entity.TransactionType;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class CsvTransactionImportService {

    private static final Set<String> REQUIRED_HEADERS = Set.of(
            "amount",
            "type",
            "category",
            "description",
            "transactionDate"
    );

    private static final int BATCH_SIZE = 50;

    private record ImportRow(
            long rowNumber,
            CreateTransactionRequest request) {
    }

    private final FinancialTransactionService transactionService;
    private final Validator validator;

    public CsvTransactionImportService(
            FinancialTransactionService transactionService,
            Validator validator) {
        this.transactionService = transactionService;
        this.validator = validator;
    }

    
    public CsvImportResponse importCsv(MultipartFile file) {

        long startTime = System.nanoTime();

        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "CSV file must not be empty"
            );
        }

        int imported = 0;
        int failed = 0;
        List<String> errors = new ArrayList<>();
        List<ImportRow> validRows = new ArrayList<>();

        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .get();

        try (Reader reader = new InputStreamReader(
                    file.getInputStream(), StandardCharsets.UTF_8);
            CSVParser parser = CSVParser.parse(reader, format)) {

            if (!parser.getHeaderMap().keySet()
                    .containsAll(REQUIRED_HEADERS)) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "CSV must contain these headers: "
                                + REQUIRED_HEADERS
                );
            }

            // Phase 1: Parse and validate every row.
            for (CSVRecord record : parser) {
                long rowNumber = record.getRecordNumber() + 1;
                CreateTransactionRequest request;

                try {
                    request = toRequest(record);
                } catch (RuntimeException ex) {
                    failed++;
                    errors.add("Row " + rowNumber
                            + ": Invalid field value");
                    continue;
                }

                Set<ConstraintViolation<CreateTransactionRequest>>
                        violations = validator.validate(request);

                if (!violations.isEmpty()) {
                    failed++;

                    for (ConstraintViolation<CreateTransactionRequest>
                            violation : violations) {
                        errors.add("Row " + rowNumber + ": "
                                + violation.getPropertyPath() + " "
                                + violation.getMessage());
                    }

                    continue;
                }

                validRows.add(new ImportRow(rowNumber, request));
            }

        } catch (IOException ex) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Unable to read CSV file",
                    ex
            );
        }

        // Phase 2: Save valid rows in independent transactions of 50.
        for (int start = 0; start < validRows.size();
                start += BATCH_SIZE) {

            int end = Math.min(start + BATCH_SIZE, validRows.size());
            List<ImportRow> batch = validRows.subList(start, end);

            List<CreateTransactionRequest> requests = batch.stream()
                    .map(ImportRow::request)
                    .toList();

            try {
                transactionService.createBulk(requests);
                imported += batch.size();

            } catch (DataAccessException ex) {
                failed += batch.size();

                for (ImportRow row : batch) {
                    errors.add("Row " + row.rowNumber()
                            + ": Database error while saving transaction");
                }
            }
        }

        long processingTimeMs =
                (System.nanoTime() - startTime) / 1_000_000;

        return new CsvImportResponse(
                imported, failed, errors, processingTimeMs);
    }


    private CreateTransactionRequest toRequest(CSVRecord record) {

        BigDecimal amount =
                new BigDecimal(record.get("amount").trim());

        TransactionType type = TransactionType.valueOf(
                record.get("type").trim().toUpperCase(Locale.ROOT)
        );

        String category = record.get("category").trim();
        String description = record.get("description").trim();

        LocalDate transactionDate =
                LocalDate.parse(record.get("transactionDate").trim());

        return new CreateTransactionRequest(
                amount,
                type,
                category,
                description,
                transactionDate
        );
    }
}
