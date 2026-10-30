package com.learning.financeflow.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "finance_csv_import_rows")
@IdClass(CsvImportRowKey.class)
public class CsvImportRow {

    @Id
    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Id
    @Column(name = "file_hash", nullable = false, length = 64)
    private String fileHash;

    @Id
    @Column(name = "row_number", nullable = false)
    private long rowNumber;

    @Column(name = "transaction_id")
    private Long transactionId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected CsvImportRow() {
    }
}
