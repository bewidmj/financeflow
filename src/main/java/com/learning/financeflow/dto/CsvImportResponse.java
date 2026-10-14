
package com.learning.financeflow.dto;

import java.util.List;

public record CsvImportResponse(
        int imported,
        int failed,
        List<String> errors
) {
}
