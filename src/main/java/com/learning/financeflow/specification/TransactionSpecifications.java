
package com.learning.financeflow.specification;

import com.learning.financeflow.entity.FinancialTransaction;
import com.learning.financeflow.entity.TransactionType;

import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.UUID;

public final class TransactionSpecifications {

    private TransactionSpecifications() {
    }

    public static Specification<FinancialTransaction> hasType(
            TransactionType type) {
        return (root, query, cb) ->
                type == null
                        ? cb.conjunction()
                        : cb.equal(root.get("type"), type);
    }

    public static Specification<FinancialTransaction> hasOwnerId(UUID ownerId) {
        return (root, query, cb) -> cb.equal(root.get("ownerId"), ownerId);
    }

    public static Specification<FinancialTransaction> hasCategory(
            String category) {
        return (root, query, cb) ->
                category == null || category.isBlank()
                        ? cb.conjunction()
                        : cb.equal(
                                cb.lower(root.get("category")),
                                category.trim().toLowerCase()
                        );
    }

    public static Specification<FinancialTransaction> dateOnOrAfter(
            LocalDate from) {
        return (root, query, cb) ->
                from == null
                        ? cb.conjunction()
                        : cb.greaterThanOrEqualTo(
                                root.get("transactionDate"), from);
    }

    public static Specification<FinancialTransaction> dateOnOrBefore(
            LocalDate to) {
        return (root, query, cb) ->
                to == null
                        ? cb.conjunction()
                        : cb.lessThanOrEqualTo(
                                root.get("transactionDate"), to);
    }
}
