package com.swiftpay.ledgerservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftpay.ledgerservice.entity.Account;
import com.swiftpay.ledgerservice.entity.LedgerTransaction;
import com.swiftpay.ledgerservice.entity.LedgerTransactionStatus;
import com.swiftpay.ledgerservice.event.PaymentInitiatedEvent;
import com.swiftpay.ledgerservice.repository.AccountRepository;
import com.swiftpay.ledgerservice.repository.LedgerTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LedgerServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private LedgerTransactionRepository ledgerTransactionRepository;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private LedgerService ledgerService;

    @BeforeEach
    void setUp() {
        ledgerService = new LedgerService(
                accountRepository,
                ledgerTransactionRepository,
                kafkaTemplate,
                objectMapper
        );
    }

    @Test
    void shouldCompletePaymentWhenBalanceIsSufficient() throws Exception {

        Account sender =
                new Account("ACC001", new BigDecimal("5000.00"), "INR");

        Account receiver =
                new Account("ACC002", new BigDecimal("1000.00"), "INR");

        PaymentInitiatedEvent event =
                new PaymentInitiatedEvent(
                UUID.randomUUID(),
                "ACC001",
                "ACC002",
                new BigDecimal("500.00"),
                "INR",
                "PAY-UNIT-001"
        );
        when(ledgerTransactionRepository.findByTransactionId("PAY-UNIT-001"))
                .thenReturn(Optional.empty());

        when(accountRepository.findByAccountIdForUpdate("ACC001"))
                .thenReturn(Optional.of(sender));

        when(accountRepository.findByAccountIdForUpdate("ACC002"))
                .thenReturn(Optional.of(receiver));

        ledgerService.processPayment(
                objectMapper.writeValueAsString(event)
        );

        assertEquals(
                new BigDecimal("4500.00"),
                sender.getBalance()
        );

        assertEquals(
                new BigDecimal("1500.00"),
                receiver.getBalance()
        );

        verify(ledgerTransactionRepository).save(
                argThat(transaction ->
                        transaction.getTransactionId().equals("PAY-UNIT-001")
                                && transaction.getStatus()
                                == LedgerTransactionStatus.COMPLETED
                )
        );

        verify(kafkaTemplate).send(
                eq("payment-completed"),
                eq("PAY-UNIT-001"),
                anyString()
        );
    }

    @Test
    void shouldFailPaymentWhenBalanceIsInsufficient() throws Exception {

        Account sender =
                new Account("ACC001", new BigDecimal("100.00"), "INR");

        Account receiver =
                new Account("ACC002", new BigDecimal("1000.00"), "INR");

        PaymentInitiatedEvent event =
                new PaymentInitiatedEvent(
                        UUID.randomUUID(),
                        "ACC001",
                        "ACC002",
                        new BigDecimal("500.00"),
                        "INR",
                        "PAY-UNIT-002"
                );

        when(ledgerTransactionRepository.findByTransactionId("PAY-UNIT-002"))
                .thenReturn(Optional.empty());

        when(accountRepository.findByAccountIdForUpdate("ACC001"))
                .thenReturn(Optional.of(sender));

        when(accountRepository.findByAccountIdForUpdate("ACC002"))
                .thenReturn(Optional.of(receiver));

        ledgerService.processPayment(
                objectMapper.writeValueAsString(event)
        );

        assertEquals(
                new BigDecimal("100.00"),
                sender.getBalance()
        );

        assertEquals(
                new BigDecimal("1000.00"),
                receiver.getBalance()
        );

        verify(ledgerTransactionRepository).save(
                argThat(transaction ->
                        transaction.getTransactionId().equals("PAY-UNIT-002")
                                && transaction.getStatus()
                                == LedgerTransactionStatus.FAILED
                )
        );

        verify(kafkaTemplate).send(
                eq("payment-failed"),
                eq("PAY-UNIT-002"),
                anyString()
        );
    }
}