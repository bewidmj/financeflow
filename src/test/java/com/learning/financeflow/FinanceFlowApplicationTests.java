package com.learning.financeflow;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.Base64;

@SpringBootTest
class FinanceFlowApplicationTests {

	@DynamicPropertySource
	static void configureJwtSecret(DynamicPropertyRegistry registry) {
		registry.add("JWT_SECRET", () -> Base64.getEncoder()
				.encodeToString(new byte[48]));
		registry.add("logging.file.name", () -> "target/test-financeflow.log");
	}

	@Test
	void contextLoads() {
	}

}
