
package com.learning.financeflow.dto;

import java.util.List;

public record CsvImportResponse(
        int imported,
        int failed,
        int skipped,
        List<String> errors,
        long processingTimeMs
) {
    public CsvImportResponse(int imported, int failed, List<String> errors, long processingTimeMs) {
        this(imported, failed, 0, errors, processingTimeMs);
    }
}
