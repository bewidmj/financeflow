package com.learning.financeflow.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String BEARER_AUTHENTICATION = "Bearer Authentication";

    @Bean
    public OpenAPI financeFlowOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("FinanceFlow API")
                        .version("v1")
                        .description(
                            "Personal finance and cashflow management API"))
                .components(new Components()
                        .addSecuritySchemes(
                                BEARER_AUTHENTICATION,
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description(
                                                "Paste the access token issued by FinanceFlow Identity Service.")))
                .addSecurityItem(new SecurityRequirement()
                        .addList(BEARER_AUTHENTICATION));
    }
}