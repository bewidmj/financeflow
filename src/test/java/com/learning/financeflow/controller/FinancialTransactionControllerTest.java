
package com.learning.financeflow.controller;

import com.learning.financeflow.dto.CreateTransactionRequest;
import com.learning.financeflow.dto.TransactionResponse;
import com.learning.financeflow.entity.TransactionType;
import com.learning.financeflow.exception.GlobalExceptionHandler;
import com.learning.financeflow.service.FinancialTransactionService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(FinancialTransactionController.class)
@Import(GlobalExceptionHandler.class)
class FinancialTransactionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FinancialTransactionService service;

    private TransactionResponse sampleResponse() {
        return new TransactionResponse(
                1L,
                new BigDecimal("1250.50"),
                TransactionType.EXPENSE,
                "GROCERIES",
                "Weekly groceries",
                LocalDate.of(2026, 10, 12)
        );
    }

    @Test
    void createShouldReturn201AndTransaction() throws Exception {
        when(service.create(any(CreateTransactionRequest.class)))
                .thenReturn(sampleResponse());

        mockMvc.perform(post("/api/v1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "amount": 1250.50,
                                  "type": "EXPENSE",
                                  "category": "GROCERIES",
                                  "description": "Weekly groceries",
                                  "transactionDate": "2026-10-12"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.amount").value(1250.50))
                .andExpect(jsonPath("$.type").value("EXPENSE"))
                .andExpect(jsonPath("$.category").value("GROCERIES"));

        verify(service).create(any(CreateTransactionRequest.class));
    }

    @Test
    void findAllShouldReturn200AndTransactions() throws Exception {
        when(service.findAll()).thenReturn(List.of(sampleResponse()));

        mockMvc.perform(get("/api/v1/transactions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].category").value("GROCERIES"));

        verify(service).findAll();
    }

    @Test
    void findByIdShouldReturn200WhenTransactionExists()
            throws Exception {
        when(service.findById(1L)).thenReturn(sampleResponse());

        mockMvc.perform(get("/api/v1/transactions/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.type").value("EXPENSE"));

        verify(service).findById(1L);
    }

    @Test
    void findByIdShouldReturn404WhenTransactionDoesNotExist()
            throws Exception {
        when(service.findById(999L))
                .thenThrow(new org.springframework.web.server
                        .ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND,
                        "Transaction not found with id: 999"
                ));

        mockMvc.perform(get("/api/v1/transactions/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message")
                        .value("Transaction not found with id: 999"));

        verify(service).findById(999L);
    }

    @Test
    void createShouldReturn400WhenAmountIsNegative()
            throws Exception {
        mockMvc.perform(post("/api/v1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "amount": -100,
                                  "type": "EXPENSE",
                                  "category": "GROCERIES",
                                  "description": "Invalid transaction",
                                  "transactionDate": "2026-10-12"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.fieldErrors.amount")
                        .exists());

        verifyNoInteractions(service);
    }

    @Test
    void createShouldReturn400ForMalformedJson() throws Exception {
        mockMvc.perform(post("/api/v1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount": 100, "type":
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message")
                        .value("Invalid or malformed request body"));

        verifyNoInteractions(service);
    }
}