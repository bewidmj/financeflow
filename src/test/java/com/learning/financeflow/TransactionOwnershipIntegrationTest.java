package com.learning.financeflow;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.learning.financeflow.entity.FinancialTransaction;
import com.learning.financeflow.entity.TransactionType;
import com.learning.financeflow.repository.BudgetRepository;
import com.learning.financeflow.repository.FinancialTransactionRepository;
import com.learning.financeflow.outbox.OutboxEventRepository;
import com.learning.financeflow.service.FinancialTransactionService;
import com.learning.financeflow.dto.CreateTransactionRequest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TransactionOwnershipIntegrationTest {

    private static final UUID USER_ONE =
            UUID.fromString("d9428888-122b-11e1-b85c-61cd3cbb3210");
    private static final UUID USER_TWO =
            UUID.fromString("c1e11e17-85d1-4e90-b20b-4f31cf6f36f3");
    @DynamicPropertySource
    static void configureJwksUri(DynamicPropertyRegistry registry) {
        registry.add("JWT_JWKS_URI", JwtTestSupport::jwksUri);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FinancialTransactionRepository transactionRepository;

    @Autowired
    private BudgetRepository budgetRepository;

    @Autowired
    private OutboxEventRepository outboxRepository;

    @Autowired
    private FinancialTransactionService transactionService;

    @BeforeEach
    void clearTransactions() {
        outboxRepository.deleteAll();
        budgetRepository.deleteAll();
        transactionRepository.deleteAll();
    }

    @Test
    void transactionByIdIsOnlyVisibleToItsOwner() throws Exception {
        String ownTransaction = """
                {
                  "amount": 25.50,
                  "type": "EXPENSE",
                  "category": "GROCERIES",
                  "description": "Owned",
                  "transactionDate": "2026-10-12"
                }
                """;
        String response = mockMvc.perform(post("/api/v1/transactions")
                        .header("Authorization", bearer(USER_ONE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ownTransaction))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long transactionId = ((Number) JsonPath.read(response, "$.id"))
                .longValue();

        mockMvc.perform(get("/api/v1/transactions/{id}", transactionId)
                        .header("Authorization", bearer(USER_ONE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(transactionId));
        org.junit.jupiter.api.Assertions.assertEquals(1, outboxRepository.count());

        mockMvc.perform(get("/api/v1/transactions/{id}", transactionId)
                        .header("Authorization", bearer(USER_TWO)))
                .andExpect(status().isNotFound());
    }

    @Test
    void transactionUpdateAndDeleteValidateInputAndEnforceOwnership()
            throws Exception {
        String response = mockMvc.perform(post("/api/v1/transactions")
                        .header("Authorization", bearer(USER_ONE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "amount": 25.50,
                                  "type": "EXPENSE",
                                  "category": "ORIGINAL",
                                  "description": "Owned",
                                  "transactionDate": "2026-10-12"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(response, "$.id")).longValue();
        String updated = """
                {
                  "amount": 30.25,
                  "type": "INCOME",
                  "category": "UPDATED",
                  "description": "Revised",
                  "transactionDate": "2026-10-13"
                }
                """;

        mockMvc.perform(put("/api/v1/transactions/{id}", id)
                        .header("Authorization", bearer(USER_TWO))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updated))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/v1/transactions/{id}", id)
                        .header("Authorization", bearer(USER_TWO)))
                .andExpect(status().isNotFound());
        mockMvc.perform(put("/api/v1/transactions/{id}", id)
                        .header("Authorization", bearer(USER_ONE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "amount": 0,
                                  "type": "EXPENSE",
                                  "category": "INVALID",
                                  "transactionDate": "2026-10-13"
                                }
                                """))
                .andExpect(status().isBadRequest());

        mockMvc.perform(put("/api/v1/transactions/{id}", id)
                        .header("Authorization", bearer(USER_ONE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updated))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(30.25))
                .andExpect(jsonPath("$.type").value("INCOME"))
                .andExpect(jsonPath("$.category").value("UPDATED"))
                .andExpect(jsonPath("$.description").value("Revised"))
                .andExpect(jsonPath("$.transactionDate").value("2026-10-13"));

        mockMvc.perform(delete("/api/v1/transactions/{id}", id)
                        .header("Authorization", bearer(USER_ONE)))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/transactions/{id}", id)
                        .header("Authorization", bearer(USER_ONE)))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/v1/transactions/{id}", id)
                        .header("Authorization", bearer(USER_ONE)))
                .andExpect(status().isNotFound());
    }

    @Test
    void adminTransactionListingRequiresAdminRole() throws Exception {
        createTransaction(USER_ONE, "ADMIN_VIEW");

        mockMvc.perform(get("/api/v1/admin/users/{ownerId}/transactions", USER_ONE)
                        .header("Authorization", bearer(USER_TWO)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/admin/users/{ownerId}/transactions", USER_ONE)
                        .header("Authorization", adminBearer())
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].category").value("ADMIN_VIEW"));
    }

    @Test
    void transactionListsAreScopedToTheAuthenticatedUser() throws Exception {
        createTransaction(USER_ONE, "USER_ONE");
        createTransaction(USER_TWO, "USER_TWO");

        mockMvc.perform(get("/api/v1/transactions")
                        .header("Authorization", bearer(USER_ONE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].category").value("USER_ONE"));

        mockMvc.perform(get("/api/v1/transactions")
                        .header("Authorization", bearer(USER_ONE))
                        .param("ownerId", USER_TWO.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].category").value("USER_ONE"));

        mockMvc.perform(get("/api/v1/transactions")
                        .header("Authorization", bearer(USER_TWO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].category").value("USER_TWO"));
    }

    @Test
    void bulkCreationAssignsEveryTransactionToTheAuthenticatedUser()
            throws Exception {
        mockMvc.perform(post("/api/v1/transactions/bulk")
                        .header("Authorization", bearer(USER_ONE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                [
                                  {
                                    "amount": 10.00,
                                    "type": "EXPENSE",
                                    "category": "BULK_ONE",
                                    "transactionDate": "2026-10-12"
                                  },
                                  {
                                    "amount": 20.00,
                                    "type": "EXPENSE",
                                    "category": "BULK_TWO",
                                    "transactionDate": "2026-10-13"
                                  }
                                ]
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.created").value(2));

        mockMvc.perform(get("/api/v1/transactions")
                        .header("Authorization", bearer(USER_TWO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));

        mockMvc.perform(get("/api/v1/transactions")
                        .header("Authorization", bearer(USER_ONE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void csvImportAssignsEveryTransactionToTheAuthenticatedUser()
            throws Exception {
        byte[] csv = """
                amount,type,category,description,transactionDate,accountId
                12.50,EXPENSE,CSV_USER_ONE,Imported,2026-10-12,9a367ac2-ae09-4da9-9b30-7cb9ee1439be
                """.getBytes(java.nio.charset.StandardCharsets.UTF_8);

        mockMvc.perform(multipart("/api/v1/transactions/import")
                        .file("file", csv)
                        .header("Authorization", bearer(USER_ONE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imported").value(1))
                .andExpect(jsonPath("$.failed").value(0));
        mockMvc.perform(multipart("/api/v1/transactions/import")
                        .file("file", csv)
                        .header("Authorization", bearer(USER_ONE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imported").value(0))
                .andExpect(jsonPath("$.skipped").value(1));
        org.junit.jupiter.api.Assertions.assertEquals(1, outboxRepository.count());

        mockMvc.perform(get("/api/v1/transactions")
                        .header("Authorization", bearer(USER_TWO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));

        mockMvc.perform(get("/api/v1/transactions")
                        .header("Authorization", bearer(USER_ONE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].category")
                        .value("CSV_USER_ONE"));
    }

    @Test
    void failedTransactionPersistenceDoesNotLeaveAnOutboxEvent() {
        org.junit.jupiter.api.Assertions.assertThrows(
                RuntimeException.class,
                () -> transactionService.create(
                        new CreateTransactionRequest(
                                null,
                                TransactionType.EXPENSE,
                                "INVALID",
                                "Must roll back",
                                LocalDate.of(2026, 10, 12)),
                        USER_ONE));

        org.junit.jupiter.api.Assertions.assertEquals(0, transactionRepository.count());
        org.junit.jupiter.api.Assertions.assertEquals(0, outboxRepository.count());
    }

    @Test
    void legacyUnassignedTransactionsAreNotReturnedToUsers() throws Exception {
        transactionRepository.save(new FinancialTransaction(
                null,
                new BigDecimal("9.99"),
                TransactionType.EXPENSE,
                "LEGACY",
                "Unassigned legacy record",
                LocalDate.of(2026, 10, 12)));

        mockMvc.perform(get("/api/v1/transactions")
                        .header("Authorization", bearer(USER_ONE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void usersCannotReadUpdateOrDeleteAnotherUsersBudget() throws Exception {
        String response = createBudget(USER_ONE, "TRAVEL", "2026-10", "200.00");
        long budgetId = ((Number) JsonPath.read(response, "$.id")).longValue();

        mockMvc.perform(get("/api/v1/budgets/{id}", budgetId)
                        .header("Authorization", bearer(USER_TWO)))
                .andExpect(status().isNotFound());

        mockMvc.perform(put("/api/v1/budgets/{id}", budgetId)
                        .header("Authorization", bearer(USER_TWO))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(budgetBody("STOLEN", "2026-10", "1.00")))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/v1/budgets/{id}", budgetId)
                        .header("Authorization", bearer(USER_TWO)))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/budgets/{id}", budgetId)
                        .header("Authorization", bearer(USER_ONE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.category").value("TRAVEL"));

        mockMvc.perform(put("/api/v1/budgets/{id}", budgetId)
                        .header("Authorization", bearer(USER_ONE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(budgetBody("HOLIDAY", "2026-10", "250.00")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.category").value("HOLIDAY"));

        mockMvc.perform(delete("/api/v1/budgets/{id}", budgetId)
                        .header("Authorization", bearer(USER_ONE)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/budgets/{id}", budgetId)
                        .header("Authorization", bearer(USER_ONE)))
                .andExpect(status().isNotFound());
    }

    @Test
    void budgetsAndSpendingAreScopedPerUser() throws Exception {
        saveTransaction(USER_ONE, "DINING", "12.50");
        saveTransaction(USER_TWO, "DINING", "90.00");

        String firstBudget = createBudget(
                USER_ONE, "DINING", "2026-10", "100.00");
        String secondBudget = createBudget(
                USER_TWO, "DINING", "2026-10", "200.00");

        org.junit.jupiter.api.Assertions.assertEquals(
                12.50, JsonPath.read(firstBudget, "$.spentAmount"), 0.001);
        org.junit.jupiter.api.Assertions.assertEquals(
                90.00, JsonPath.read(secondBudget, "$.spentAmount"), 0.001);

        mockMvc.perform(get("/api/v1/budgets")
                        .header("Authorization", bearer(USER_ONE))
                        .param("month", "2026-10")
                        .param("ownerId", USER_TWO.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].spentAmount").value(12.50));

        mockMvc.perform(get("/api/v1/budgets")
                        .header("Authorization", bearer(USER_TWO))
                        .param("month", "2026-10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].spentAmount").value(90.00));
    }

    @Test
    void differentUsersCanCreateSameCategoryBudgetForSameMonth()
            throws Exception {
        String firstBudget = createBudget(
                USER_ONE, "SHARED_CATEGORY", "2026-10", "100.00");
        String secondBudget = createBudget(
                USER_TWO, "SHARED_CATEGORY", "2026-10", "200.00");

        long firstId = ((Number) JsonPath.read(firstBudget, "$.id"))
                .longValue();
        long secondId = ((Number) JsonPath.read(secondBudget, "$.id"))
                .longValue();

        org.junit.jupiter.api.Assertions.assertNotEquals(firstId, secondId);
        mockMvc.perform(get("/api/v1/budgets/{id}", firstId)
                        .header("Authorization", bearer(USER_TWO)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/budgets/{id}", secondId)
                        .header("Authorization", bearer(USER_TWO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.budgetAmount").value(200.00));
    }

    @Test
    void legacyUnassignedBudgetsAreNotReturnedToUsers() throws Exception {
        budgetRepository.save(new com.learning.financeflow.entity.Budget(
                null,
                "LEGACY_BUDGET",
                LocalDate.of(2026, 10, 1),
                new BigDecimal("50.00")));

        mockMvc.perform(get("/api/v1/budgets")
                        .header("Authorization", bearer(USER_ONE))
                        .param("month", "2026-10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    private void createTransaction(UUID userId, String category)
            throws Exception {
        mockMvc.perform(post("/api/v1/transactions")
                        .header("Authorization", bearer(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "amount": 25.50,
                                  "type": "EXPENSE",
                                  "category": "%s",
                                  "transactionDate": "2026-10-12"
                                }
                                """.formatted(category)))
                .andExpect(status().isCreated());
    }

    private void saveTransaction(UUID ownerId, String category, String amount) {
        transactionRepository.save(new FinancialTransaction(
                ownerId,
                new BigDecimal(amount),
                TransactionType.EXPENSE,
                category,
                "Integration test expense",
                LocalDate.of(2026, 10, 12)));
    }

    private String createBudget(
            UUID ownerId, String category, String month, String amount)
            throws Exception {
        return mockMvc.perform(post("/api/v1/budgets")
                        .header("Authorization", bearer(ownerId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(budgetBody(category, month, amount)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }

    private String budgetBody(String category, String month, String amount) {
        return """
                {
                  "category": "%s",
                  "month": "%s",
                  "amount": %s
                }
                """.formatted(category, month, amount);
    }

    private String bearer(UUID userId) {
        return "Bearer " + JwtTestSupport.token(userId);
    }

    private String adminBearer() {
        return "Bearer " + JwtTestSupport.token(USER_TWO, "ADMIN");
    }
}
