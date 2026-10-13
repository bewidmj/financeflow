
package com.learning.financeflow.service;

import com.learning.financeflow.dto.CreateTransactionRequest;
import com.learning.financeflow.dto.TransactionResponse;
import com.learning.financeflow.entity.FinancialTransaction;
import com.learning.financeflow.entity.TransactionType;
import com.learning.financeflow.repository.FinancialTransactionRepository;

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
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FinancialTransactionServiceTest {

    @Mock
    private FinancialTransactionRepository repository;

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

        TransactionResponse response = service.create(request);

        ArgumentCaptor<FinancialTransaction> captor =
                ArgumentCaptor.forClass(FinancialTransaction.class);

        verify(repository).save(captor.capture());

        FinancialTransaction saved = captor.getValue();

        assertEquals(request.amount(), saved.getAmount());
        assertEquals(request.type(), saved.getType());
        assertEquals(request.category(), saved.getCategory());
        assertEquals(request.description(), saved.getDescription());
        assertEquals(request.transactionDate(), saved.getTransactionDate());

        assertEquals(request.amount(), response.amount());
        assertEquals(request.type(), response.type());
        assertEquals(request.category(), response.category());
        assertEquals(request.description(), response.description());
        assertEquals(request.transactionDate(), response.transactionDate());
    }

    @Test
    void findAllShouldReturnMappedResponses() {
        FinancialTransaction first = new FinancialTransaction(
                new BigDecimal("1250.50"),
                TransactionType.EXPENSE,
                "GROCERIES",
                "Weekly groceries",
                LocalDate.of(2026, 10, 12)
        );

        FinancialTransaction second = new FinancialTransaction(
                new BigDecimal("5000.00"),
                TransactionType.INCOME,
                "SALARY",
                "Monthly salary",
                LocalDate.of(2026, 10, 1)
        );

        when(repository.findAll()).thenReturn(List.of(first, second));

        List<TransactionResponse> responses = service.findAll();

        assertEquals(2, responses.size());
        assertEquals(new BigDecimal("1250.50"), responses.get(0).amount());
        assertEquals(TransactionType.EXPENSE, responses.get(0).type());
        assertEquals("GROCERIES", responses.get(0).category());

        assertEquals(new BigDecimal("5000.00"), responses.get(1).amount());
        assertEquals(TransactionType.INCOME, responses.get(1).type());
        assertEquals("SALARY", responses.get(1).category());

        verify(repository).findAll();
    }

    @Test
    void findByIdShouldReturnTransactionWhenFound() {
        FinancialTransaction transaction = new FinancialTransaction(
                new BigDecimal("1250.50"),
                TransactionType.EXPENSE,
                "GROCERIES",
                "Weekly groceries",
                LocalDate.of(2026, 10, 12)
        );

        when(repository.findById(1L))
                .thenReturn(Optional.of(transaction));

        TransactionResponse response = service.findById(1L);

        assertEquals(new BigDecimal("1250.50"), response.amount());
        assertEquals(TransactionType.EXPENSE, response.type());
        assertEquals("GROCERIES", response.category());

        verify(repository).findById(1L);
    }

    @Test
    void findByIdShouldThrowNotFoundWhenTransactionDoesNotExist() {
        when(repository.findById(999L)).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.findById(999L)
        );

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        assertEquals(
                "Transaction not found with id: 999",
                exception.getReason()
        );

        verify(repository).findById(999L);
    }
}
