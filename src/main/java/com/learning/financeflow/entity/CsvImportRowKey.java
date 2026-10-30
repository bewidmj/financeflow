package com.learning.financeflow.entity;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public class CsvImportRowKey implements Serializable {

    private UUID ownerId;
    private String fileHash;
    private long rowNumber;

    public CsvImportRowKey() {
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof CsvImportRowKey key)) {
            return false;
        }
        return rowNumber == key.rowNumber
                && Objects.equals(ownerId, key.ownerId)
                && Objects.equals(fileHash, key.fileHash);
    }

    @Override
    public int hashCode() {
        return Objects.hash(ownerId, fileHash, rowNumber);
    }
}
