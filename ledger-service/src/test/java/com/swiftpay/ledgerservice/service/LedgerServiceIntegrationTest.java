package com.swiftpay.ledgerservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftpay.ledgerservice.entity.Account;
import com.swiftpay.ledgerservice.entity.LedgerTransactionStatus;
import com.swiftpay.ledgerservice.event.PaymentInitiatedEvent;
import com.swiftpay.ledgerservice.repository.AccountRepository;
import com.swiftpay.ledgerservice.repository.LedgerTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.kafka.listener.auto-startup=false"
        }
)
class LedgerServiceIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:17")
                    .withDatabaseName("swiftpay")
                    .withUsername("swiftpay")
                    .withPassword("swiftpay");

    @DynamicPropertySource
    static void configureDatabase(DynamicPropertyRegistry registry) {
        registry.add(
                "spring.datasource.url",
                postgres::getJdbcUrl
        );

        registry.add(
                "spring.datasource.username",
                postgres::getUsername
        );

        registry.add(
                "spring.datasource.password",
                postgres::getPassword
        );
    }

    @Autowired
    private LedgerService ledgerService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private LedgerTransactionRepository ledgerTransactionRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private KafkaTemplate<String, String> kafkaTemplate;

    @BeforeEach
    void resetDatabase() {

        ledgerTransactionRepository.deleteAll();

        accountRepository.deleteAll();

        accountRepository.save(
                new Account(
                        "ACC001",
                        new BigDecimal("5000.00"),
                        "INR"
                )
        );

        accountRepository.save(
                new Account(
                        "ACC002",
                        new BigDecimal("1000.00"),
                        "INR"
                )
        );
    }

    @Test
    void shouldCompletePaymentAndUpdateBothBalances() throws Exception {

        PaymentInitiatedEvent event =
                new PaymentInitiatedEvent(
                        UUID.randomUUID(),
                        "ACC001",
                        "ACC002",
                        new BigDecimal("500.00"),
                        "INR",
                        "PAY-INT-001"
                );

        ledgerService.processPayment(
                objectMapper.writeValueAsString(event)
        );

        Account sender =
                accountRepository
                        .findByAccountId("ACC001")
                        .orElseThrow();

        Account receiver =
                accountRepository
                        .findByAccountId("ACC002")
                        .orElseThrow();

        assertEquals(
                new BigDecimal("4500.00"),
                sender.getBalance()
        );

        assertEquals(
                new BigDecimal("1500.00"),
                receiver.getBalance()
        );

        var transaction =
                ledgerTransactionRepository
                        .findByTransactionId("PAY-INT-001")
                        .orElseThrow();

        assertEquals(
                LedgerTransactionStatus.COMPLETED,
                transaction.getStatus()
        );
    }

    @Test
    void shouldRejectPaymentWhenBalanceIsInsufficient() throws Exception {

        PaymentInitiatedEvent event =
                new PaymentInitiatedEvent(
                        UUID.randomUUID(),
                        "ACC001",
                        "ACC002",
                        new BigDecimal("6000.00"),
                        "INR",
                        "PAY-INT-002"
                );

        ledgerService.processPayment(
                objectMapper.writeValueAsString(event)
        );

        Account sender =
                accountRepository
                        .findByAccountId("ACC001")
                        .orElseThrow();

        Account receiver =
                accountRepository
                        .findByAccountId("ACC002")
                        .orElseThrow();

        assertEquals(
                new BigDecimal("5000.00"),
                sender.getBalance()
        );

        assertEquals(
                new BigDecimal("1000.00"),
                receiver.getBalance()
        );

        var transaction =
                ledgerTransactionRepository
                        .findByTransactionId("PAY-INT-002")
                        .orElseThrow();

        assertEquals(
                LedgerTransactionStatus.FAILED,
                transaction.getStatus()
        );
    }

    @Test
    void shouldIgnoreDuplicateTransaction() throws Exception {

        PaymentInitiatedEvent event =
                new PaymentInitiatedEvent(
                        UUID.randomUUID(),
                        "ACC001",
                        "ACC002",
                        new BigDecimal("100.00"),
                        "INR",
                        "PAY-INT-003"
                );

        String message =
                objectMapper.writeValueAsString(event);

        ledgerService.processPayment(message);

        ledgerService.processPayment(message);

        Account sender =
                accountRepository
                        .findByAccountId("ACC001")
                        .orElseThrow();

        Account receiver =
                accountRepository
                        .findByAccountId("ACC002")
                        .orElseThrow();

        assertEquals(
                new BigDecimal("4900.00"),
                sender.getBalance()
        );

        assertEquals(
                new BigDecimal("1100.00"),
                receiver.getBalance()
        );

        assertEquals(
                1,
                ledgerTransactionRepository
                        .findAll()
                        .stream()
                        .filter(transaction ->
                                transaction
                                        .getTransactionId()
                                        .equals("PAY-INT-003")
                        )
                        .count()
        );
    }

    @Test
    void shouldFailWhenAccountDoesNotExist() throws Exception {

        PaymentInitiatedEvent event =
                new PaymentInitiatedEvent(
                        UUID.randomUUID(),
                        "UNKNOWN",
                        "ACC002",
                        new BigDecimal("100.00"),
                        "INR",
                        "PAY-INT-004"
                );

        ledgerService.processPayment(
                objectMapper.writeValueAsString(event)
        );

        var transaction =
                ledgerTransactionRepository
                        .findByTransactionId("PAY-INT-004")
                        .orElseThrow();

        assertEquals(
                LedgerTransactionStatus.FAILED,
                transaction.getStatus()
        );

        Account receiver =
                accountRepository
                        .findByAccountId("ACC002")
                        .orElseThrow();

        assertEquals(
                new BigDecimal("1000.00"),
                receiver.getBalance()
        );
    }

    @Test
    void shouldFailWhenCurrenciesDoNotMatch() throws Exception {

        PaymentInitiatedEvent event =
                new PaymentInitiatedEvent(
                        UUID.randomUUID(),
                        "ACC001",
                        "ACC002",
                        new BigDecimal("100.00"),
                        "USD",
                        "PAY-INT-005"
                );

        ledgerService.processPayment(
                objectMapper.writeValueAsString(event)
        );

        var transaction =
                ledgerTransactionRepository
                        .findByTransactionId("PAY-INT-005")
                        .orElseThrow();

        assertEquals(
                LedgerTransactionStatus.FAILED,
                transaction.getStatus()
        );

        Account sender =
                accountRepository
                        .findByAccountId("ACC001")
                        .orElseThrow();

        Account receiver =
                accountRepository
                        .findByAccountId("ACC002")
                        .orElseThrow();

        assertEquals(
                new BigDecimal("5000.00"),
                sender.getBalance()
        );

        assertEquals(
                new BigDecimal("1000.00"),
                receiver.getBalance()
        );
    }


}