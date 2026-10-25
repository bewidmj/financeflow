package com.learning.financeflow.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.junit.jupiter.api.Test;

class OpenApiConfigTest {

    @Test
    void definesJwtBearerAuthenticationForApiOperations() {
        OpenAPI openAPI = new OpenApiConfig().financeFlowOpenAPI();

        SecurityScheme scheme = openAPI.getComponents()
                .getSecuritySchemes()
                .get("Bearer Authentication");

        assertNotNull(scheme);
        assertEquals(SecurityScheme.Type.HTTP, scheme.getType());
        assertEquals("bearer", scheme.getScheme());
        assertEquals("JWT", scheme.getBearerFormat());
        assertEquals(1, openAPI.getSecurity().size());
        assertEquals(
                java.util.List.of("Bearer Authentication"),
                openAPI.getSecurity().getFirst().keySet().stream().toList());
    }
}
