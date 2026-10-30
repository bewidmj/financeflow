package com.learning.financeflow.service;

import com.learning.financeflow.dto.CreateTransactionRequest;

public record CsvTransactionRow(long rowNumber, CreateTransactionRequest request) {
}
