
package com.learning.financeflow.dto;

import java.util.List;

public record BulkCreateTransactionResponse(
        int created,
        List<TransactionResponse> transactions
) {
}
