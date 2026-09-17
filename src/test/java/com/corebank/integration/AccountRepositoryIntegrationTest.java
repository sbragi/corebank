package com.corebank.integration;

import com.corebank.account.Account;
import com.corebank.account.AccountRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Optional integration test. It uses Docker/Testcontainers and is intentionally
 * excluded from the default `mvn test` execution.
 *
 * Run with: mvn -Pintegration test
 */
@Tag("integration")
@Testcontainers
@SpringBootTest
class AccountRepositoryIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("corebank")
            .withUsername("corebank")
            .withPassword("corebank")
            .withCommand("postgres", "-c", "wal_level=logical");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.primary.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.primary.username", postgres::getUsername);
        registry.add("spring.datasource.primary.password", postgres::getPassword);
        registry.add("spring.datasource.replica.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.replica.username", postgres::getUsername);
        registry.add("spring.datasource.replica.password", postgres::getPassword);
    }

    @Autowired
    AccountRepository repository;

    @Test
    void shouldPersistAndReadAccount() {
        Account account = new Account();
        account.setId(UUID.randomUUID());
        account.setCustomerId(UUID.randomUUID());
        account.setBalance(new BigDecimal("500.00"));
        account.setVersion(0L);

        Account saved = repository.saveAndFlush(account);

        assertEquals(
                new BigDecimal("500.00"),
                repository.findById(saved.getId()).orElseThrow().getBalance()
        );
    }
}
