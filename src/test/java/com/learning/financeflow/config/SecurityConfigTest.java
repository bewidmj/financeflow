package com.learning.financeflow.config;

import static org.springframework.security.oauth2.jose.jws.MacAlgorithm.HS256;
import static org.springframework.security.oauth2.jose.jws.MacAlgorithm.HS384;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.learning.financeflow.controller.FinancialTransactionController;
import com.learning.financeflow.service.CsvTransactionImportService;
import com.learning.financeflow.service.FinancialTransactionService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertEquals;

@WebMvcTest(FinancialTransactionController.class)
@Import(SecurityConfig.class)
class SecurityConfigTest {

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

    @MockitoBean
    private FinancialTransactionService transactionService;

    @MockitoBean
    private CsvTransactionImportService csvTransactionImportService;

    @BeforeEach
    void setUp() {
        when(transactionService.search(
                nullable(com.learning.financeflow.entity.TransactionType.class),
                nullable(String.class),
                nullable(java.time.LocalDate.class),
                nullable(java.time.LocalDate.class),
                any(Pageable.class),
                nullable(java.util.UUID.class)))
                .thenReturn(Page.empty());
    }

    @Test
    void missingTokenShouldReturnUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/transactions"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void malformedTokenShouldReturnUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/transactions")
                        .header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void invalidSignatureShouldReturnUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/transactions")
                        .header("Authorization",
                                "Bearer " + token(new byte[48])))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void hs256TokenShouldReturnUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/transactions")
                        .header("Authorization",
                                "Bearer " + token(TEST_KEY, HS256)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void validHs384TokenShouldReachApi() throws Exception {
        String validToken = token(TEST_KEY);
        var parsedToken = Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(TEST_KEY))
                .build()
                .parseSignedClaims(validToken);
        assertEquals("HS384", parsedToken.getHeader().getAlgorithm());

        mockMvc.perform(get("/api/v1/transactions")
                        .header("Authorization",
                                "Bearer " + validToken))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON));
    }

    @Test
    void modifiedJjwtTokenShouldBeRejected() throws Exception {
        String[] parts = token(TEST_KEY).split("\\.");
        parts[2] = (parts[2].charAt(0) == 'A' ? "B" : "A")
                + parts[2].substring(1);

        mockMvc.perform(get("/api/v1/transactions")
                        .header("Authorization",
                                "Bearer " + String.join(".", parts)))
                .andExpect(status().isUnauthorized());
    }

    private String token(byte[] keyBytes) {
        return token(keyBytes, HS384);
    }

    private String token(byte[] keyBytes, MacAlgorithm algorithm) {
        byte[] decodedKey = Decoders.BASE64.decode(
                Base64.getEncoder().encodeToString(keyBytes));
        var key = Keys.hmacShaKeyFor(decodedKey);
        Instant now = Instant.now();
        var builder = Jwts.builder()
                .subject("d9428888-122b-11e1-b85c-61cd3cbb3210")
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(300)))
                .claim("email", "test@example.com")
                .claim("role", "USER");
        return algorithm == HS384
                ? builder.signWith(key).compact()
                : builder.signWith(key, Jwts.SIG.HS256).compact();
    }
}
