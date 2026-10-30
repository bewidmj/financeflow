
package com.learning.financeflow.service;

import com.learning.financeflow.dto.CreateTransactionRequest;
import com.learning.financeflow.dto.TransactionResponse;
import com.learning.financeflow.entity.FinancialTransaction;
import com.learning.financeflow.entity.TransactionType;
import com.learning.financeflow.repository.FinancialTransactionRepository;
import com.learning.financeflow.repository.CsvImportRowStore;
import com.learning.financeflow.outbox.OutboxEventFactory;
import com.learning.financeflow.outbox.OutboxEventRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FinancialTransactionServiceTest {

    private static final UUID OWNER_ID =
            UUID.fromString("d9428888-122b-11e1-b85c-61cd3cbb3210");

    @Mock
    private FinancialTransactionRepository repository;

    @Mock
    private OutboxEventRepository outboxRepository;

    @Mock
    private OutboxEventFactory outboxEventFactory;

    @Mock
    private CsvImportRowStore csvImportRowStore;

    @InjectMocks
    private FinancialTransactionService service;

    private CreateTransactionRequest request;

    @BeforeEach
    void setUp() {
        request = new CreateTransactionRequest(
                new BigDecimal("1250.50"),
                TransactionType.EXPENSE,
                "GROCERIES",
                "Weekly groceries",
                LocalDate.of(2026, 10, 12)
        );
    }

    @Test
    void createShouldSaveTransactionAndReturnResponse() {
        when(repository.save(any(FinancialTransaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        TransactionResponse response = service.create(request, OWNER_ID);

        ArgumentCaptor<FinancialTransaction> captor =
                ArgumentCaptor.forClass(FinancialTransaction.class);

        verify(repository).save(captor.capture());

        FinancialTransaction saved = captor.getValue();

        assertEquals(request.amount(), saved.getAmount());
        assertEquals(request.type(), saved.getType());
        assertEquals(request.category(), saved.getCategory());
        assertEquals(request.description(), saved.getDescription());
        assertEquals(request.transactionDate(), saved.getTransactionDate());
        assertEquals(OWNER_ID, saved.getOwnerId());
        assertNull(saved.getAccountId());

        assertEquals(request.amount(), response.amount());
        assertEquals(request.type(), response.type());
        assertEquals(request.category(), response.category());
        assertEquals(request.description(), response.description());
        assertEquals(request.transactionDate(), response.transactionDate());
        assertEquals(1, response.transactionVersion());
        verify(outboxRepository).save(any());
    }

    @Test
    void findByIdShouldReturnTransactionWhenFound() {
        FinancialTransaction transaction = new FinancialTransaction(
                OWNER_ID,
                new BigDecimal("1250.50"),
                TransactionType.EXPENSE,
                "GROCERIES",
                "Weekly groceries",
                LocalDate.of(2026, 10, 12)
        );

        when(repository.findByIdAndOwnerId(1L, OWNER_ID))
                .thenReturn(Optional.of(transaction));

        TransactionResponse response = service.findById(1L, OWNER_ID);

        assertEquals(new BigDecimal("1250.50"), response.amount());
        assertEquals(TransactionType.EXPENSE, response.type());
        assertEquals("GROCERIES", response.category());

        verify(repository).findByIdAndOwnerId(1L, OWNER_ID);
    }

    @Test
    void findByIdShouldThrowNotFoundWhenTransactionDoesNotExist() {
        when(repository.findByIdAndOwnerId(999L, OWNER_ID))
                .thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.findById(999L, OWNER_ID)
        );

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        assertEquals(
                "Transaction not found with id: 999",
                exception.getReason()
        );

        verify(repository).findByIdAndOwnerId(999L, OWNER_ID);
    }
}
