package com.learning.financeflow.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI financeFlowOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("FinanceFlow API")
                        .version("v1")
                        .description(
                            "Personal finance and cashflow management API"));
    }
}