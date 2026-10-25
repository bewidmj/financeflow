package com.learning.financeflow;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest
class FinanceFlowApplicationTests {

	@DynamicPropertySource
	static void configureJwksUri(DynamicPropertyRegistry registry) {
		registry.add("JWT_JWKS_URI", JwtTestSupport::jwksUri);
		registry.add("logging.file.name", () -> "target/test-financeflow.log");
	}

	@Test
	void contextLoads() {
	}

}
