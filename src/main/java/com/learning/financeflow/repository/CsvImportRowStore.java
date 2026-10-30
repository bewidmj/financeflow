package com.learning.financeflow.repository;

import java.util.UUID;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Savepoint;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class CsvImportRowStore {

    private final JdbcTemplate jdbcTemplate;
    private final DataSource dataSource;

    public CsvImportRowStore(JdbcTemplate jdbcTemplate, DataSource dataSource) {
        this.jdbcTemplate = jdbcTemplate;
        this.dataSource = dataSource;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public boolean reserve(UUID ownerId, String fileHash, long rowNumber) {
        Connection connection = DataSourceUtils.getConnection(dataSource);
        try {
            try {
                Savepoint savepoint = connection.setSavepoint();
                try {
                    int inserted = jdbcTemplate.update("""
                            INSERT INTO finance_csv_import_rows (
                                owner_id, file_hash, row_number, created_at
                            ) VALUES (?, ?, ?, CURRENT_TIMESTAMP)
                            """, ownerId, fileHash, rowNumber);
                    connection.releaseSavepoint(savepoint);
                    return inserted == 1;
                } catch (DuplicateKeyException exception) {
                    rollbackToSavepoint(connection, savepoint, exception);
                    return false;
                } catch (RuntimeException exception) {
                    rollbackToSavepoint(connection, savepoint, exception);
                    throw exception;
                }
            } catch (java.sql.SQLException exception) {
                throw new DataAccessResourceFailureException(
                        "Unable to establish CSV import reservation savepoint", exception);
            }
        } finally {
            DataSourceUtils.releaseConnection(connection, dataSource);
        }
    }

    private void rollbackToSavepoint(
            Connection connection,
            Savepoint savepoint,
            RuntimeException originalException) {
        try {
            connection.rollback(savepoint);
            connection.releaseSavepoint(savepoint);
        } catch (java.sql.SQLException rollbackException) {
            originalException.addSuppressed(rollbackException);
            throw new DataAccessResourceFailureException(
                    "Unable to recover CSV import reservation", originalException);
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void complete(
            UUID ownerId, String fileHash, long rowNumber, Long transactionId) {
        int updated = jdbcTemplate.update("""
                UPDATE finance_csv_import_rows
                SET transaction_id = ?
                WHERE owner_id = ? AND file_hash = ? AND row_number = ?
                """, transactionId, ownerId, fileHash, rowNumber);
        if (updated != 1) {
            throw new IllegalStateException("CSV import row reservation was lost");
        }
    }
}
