package com.learning.financeflow;

import static org.springframework.security.oauth2.jose.jws.MacAlgorithm.HS384;
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
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
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
    private static final byte[] TEST_KEY = new byte[48];

    static {
        java.util.Arrays.fill(TEST_KEY, (byte) 0x42);
    }

    @DynamicPropertySource
    static void configureJwtSecret(DynamicPropertyRegistry registry) {
        registry.add("JWT_SECRET", () ->
                Base64.getEncoder().encodeToString(TEST_KEY));
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FinancialTransactionRepository transactionRepository;

    @Autowired
    private BudgetRepository budgetRepository;

    @BeforeEach
    void clearTransactions() {
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

        mockMvc.perform(get("/api/v1/transactions/{id}", transactionId)
                        .header("Authorization", bearer(USER_TWO)))
                .andExpect(status().isNotFound());
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
                amount,type,category,description,transactionDate
                12.50,EXPENSE,CSV_USER_ONE,Imported,2026-10-12
                """.getBytes(java.nio.charset.StandardCharsets.UTF_8);

        mockMvc.perform(multipart("/api/v1/transactions/import")
                        .file("file", csv)
                        .header("Authorization", bearer(USER_ONE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imported").value(1))
                .andExpect(jsonPath("$.failed").value(0));

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
        var key = new SecretKeySpec(TEST_KEY, "HmacSHA384");
        JwtEncoder encoder = NimbusJwtEncoder.withSecretKey(key)
                .algorithm(HS384)
                .build();
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(userId.toString())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .claim("email", userId + "@example.com")
                .claim("role", "USER")
                .build();
        String token = encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(HS384).build(), claims)).getTokenValue();
        return "Bearer " + token;
    }
}
