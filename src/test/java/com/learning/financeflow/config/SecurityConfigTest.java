package com.learning.financeflow.config;

import com.learning.financeflow.JwtTestSupport;
import com.learning.financeflow.controller.FinancialTransactionController;
import com.learning.financeflow.service.CsvTransactionImportService;
import com.learning.financeflow.service.FinancialTransactionService;
import io.jsonwebtoken.Jwts;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FinancialTransactionController.class)
@Import(SecurityConfig.class)
class SecurityConfigTest {

    private static final UUID SUBJECT =
            UUID.fromString("d9428888-122b-11e1-b85c-61cd3cbb3210");

    @DynamicPropertySource
    static void configureJwksUri(DynamicPropertyRegistry registry) {
        registry.add("JWT_JWKS_URI", JwtTestSupport::jwksUri);
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
                nullable(UUID.class)))
                .thenReturn(Page.empty());
    }

    @Test
    void missingTokenIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/transactions"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void malformedTokenIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/transactions")
                        .header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void invalidSignatureIsRejected() throws Exception {
        var otherPair = java.security.KeyPairGenerator.getInstance("RSA");
        otherPair.initialize(2048);
        String token = JwtTestSupport.token(
                SUBJECT, otherPair.generateKeyPair(), JwtTestSupport.KEY_ID,
                Jwts.SIG.RS256, Instant.now(), Instant.now().plusSeconds(300));

        mockMvc.perform(get("/api/v1/transactions")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void expiredTokenIsRejected() throws Exception {
        Instant now = Instant.now();
        String token = JwtTestSupport.token(
                SUBJECT, JwtTestSupport.keyPair(), JwtTestSupport.KEY_ID,
                Jwts.SIG.RS256, now.minusSeconds(600), now.minusSeconds(300));

        mockMvc.perform(get("/api/v1/transactions")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void hs384TokenIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/transactions")
                        .header("Authorization",
                                "Bearer " + JwtTestSupport.hs384Token(SUBJECT)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unsupportedRsaAlgorithmIsRejected() throws Exception {
        Instant now = Instant.now();
        String token = JwtTestSupport.token(
                SUBJECT, JwtTestSupport.keyPair(), JwtTestSupport.KEY_ID,
                Jwts.SIG.RS384, now, now.plusSeconds(300));

        mockMvc.perform(get("/api/v1/transactions")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unsignedTokenIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/transactions")
                        .header("Authorization",
                                "Bearer " + JwtTestSupport.unsignedToken(SUBJECT)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unknownKeyIdIsRejected() throws Exception {
        Instant now = Instant.now();
        String token = JwtTestSupport.token(
                SUBJECT, JwtTestSupport.keyPair(), "unknown-key",
                Jwts.SIG.RS256, now, now.plusSeconds(300));

        mockMvc.perform(get("/api/v1/transactions")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void missingKeyIdIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/transactions")
                        .header("Authorization",
                                "Bearer " + JwtTestSupport.tokenWithoutKeyId(SUBJECT)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void validRs256TokenUsesSubjectAsTheAuthenticatedPrincipal() throws Exception {
        String token = JwtTestSupport.token(SUBJECT);
        var parsed = Jwts.parser()
                .verifyWith(JwtTestSupport.keyPair().getPublic())
                .build()
                .parseSignedClaims(token);
        assertEquals("RS256", parsed.getHeader().getAlgorithm());
        assertEquals(JwtTestSupport.KEY_ID, parsed.getHeader().getKeyId());

        mockMvc.perform(get("/api/v1/transactions")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON));

        verify(transactionService).search(
                nullable(com.learning.financeflow.entity.TransactionType.class),
                nullable(String.class),
                nullable(java.time.LocalDate.class),
                nullable(java.time.LocalDate.class),
                any(Pageable.class),
                eq(SUBJECT));
    }

    @Test
    void swaggerOpenApiPathRemainsPublic() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(result -> assertNotEquals(
                        401, result.getResponse().getStatus()));
    }
}
