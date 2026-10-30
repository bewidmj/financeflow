package com.learning.financeflow.repository;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.Savepoint;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

class CsvImportRowStoreTest {

    @Test
    void reservesAnImportedCsvRowOnlyOnce() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        Savepoint savepoint = mock(Savepoint.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.setSavepoint()).thenReturn(savepoint);
        CsvImportRowStore store = new CsvImportRowStore(jdbcTemplate, dataSource);
        UUID ownerId = UUID.randomUUID();
        String fileHash = "a".repeat(64);
        doReturn(1).doThrow(new DuplicateKeyException("duplicate")).when(jdbcTemplate)
                .update(anyString(), eq(ownerId), eq(fileHash), eq(2L));
        doReturn(1).when(jdbcTemplate)
                .update(anyString(), eq(ownerId), eq(fileHash), eq(3L));

        assertTrue(store.reserve(ownerId, fileHash, 2));
        assertFalse(store.reserve(ownerId, fileHash, 2));
        assertTrue(store.reserve(ownerId, fileHash, 3));
    }
}
