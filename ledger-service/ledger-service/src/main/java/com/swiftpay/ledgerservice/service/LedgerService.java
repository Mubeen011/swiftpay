package com.swiftpay.ledgerservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftpay.ledgerservice.entity.Account;
import com.swiftpay.ledgerservice.entity.LedgerTransaction;
import com.swiftpay.ledgerservice.entity.LedgerTransactionStatus;
import com.swiftpay.ledgerservice.event.PaymentCompletedEvent;
import com.swiftpay.ledgerservice.event.PaymentFailedEvent;
import com.swiftpay.ledgerservice.event.PaymentInitiatedEvent;
import com.swiftpay.ledgerservice.repository.AccountRepository;
import com.swiftpay.ledgerservice.repository.LedgerTransactionRepository;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class LedgerService {

    private static final String PAYMENT_COMPLETED_TOPIC =
            "payment-completed";

    private static final String PAYMENT_FAILED_TOPIC =
            "payment-failed";

    private final AccountRepository accountRepository;
    private final LedgerTransactionRepository ledgerTransactionRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public LedgerService(
            AccountRepository accountRepository,
            LedgerTransactionRepository ledgerTransactionRepository,
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper
    ) {
        this.accountRepository = accountRepository;
        this.ledgerTransactionRepository = ledgerTransactionRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topics = "payment-initiated",
            groupId = "ledger-service"
    )
    @Transactional
    public void processPayment(String message) {

        try {
            PaymentInitiatedEvent event =
                    objectMapper.readValue(
                            message,
                            PaymentInitiatedEvent.class
                    );

            processPaymentEvent(event);

        } catch (Exception e) {
            throw new IllegalStateException(
                    "Failed to process PaymentInitiated event",
                    e
            );
        }
    }

    private void processPaymentEvent(
            PaymentInitiatedEvent event
    ) throws JsonProcessingException {

        LedgerTransaction existingTransaction =
                ledgerTransactionRepository
                        .findByTransactionId(event.transaction_id())
                        .orElse(null);

        if (existingTransaction != null) {
            return;
        }

        Account sender =
                accountRepository
                        .findByAccountIdForUpdate(event.sender_id())
                        .orElse(null);

        Account receiver =
                accountRepository
                        .findByAccountIdForUpdate(event.receiver_id())
                        .orElse(null);

        if (sender == null || receiver == null) {

            saveFailedTransaction(
                    event,
                    "Sender or receiver account not found"
            );

            return;
        }

        if (!sender.getCurrency().equals(event.currency())
                || !receiver.getCurrency().equals(event.currency())) {

            saveFailedTransaction(
                    event,
                    "Currency mismatch"
            );

            return;
        }

        BigDecimal amount = event.amount();

        if (amount.compareTo(BigDecimal.ZERO) <= 0) {

            saveFailedTransaction(
                    event,
                    "Amount must be greater than zero"
            );

            return;
        }

        if (sender.getBalance().compareTo(amount) < 0) {

            saveFailedTransaction(
                    event,
                    "Insufficient balance"
            );

            return;
        }

        sender.debit(amount);
        receiver.credit(amount);

        accountRepository.save(sender);
        accountRepository.save(receiver);

        LedgerTransaction transaction =
                new LedgerTransaction(
                        event.transaction_id(),
                        event.sender_id(),
                        event.receiver_id(),
                        event.amount(),
                        event.currency(),
                        LedgerTransactionStatus.COMPLETED
                );

        ledgerTransactionRepository.save(transaction);

        PaymentCompletedEvent completedEvent =
                new PaymentCompletedEvent(
                        event.transaction_id(),
                        event.sender_id(),
                        event.receiver_id(),
                        event.amount(),
                        event.currency()
                );

        String eventJson =
                objectMapper.writeValueAsString(completedEvent);

        kafkaTemplate.send(
                PAYMENT_COMPLETED_TOPIC,
                event.transaction_id(),
                eventJson
        );
    }

    private void saveFailedTransaction(
            PaymentInitiatedEvent event,
            String reason
    ) throws JsonProcessingException {

        LedgerTransaction transaction =
                new LedgerTransaction(
                        event.transaction_id(),
                        event.sender_id(),
                        event.receiver_id(),
                        event.amount(),
                        event.currency(),
                        LedgerTransactionStatus.FAILED
                );

        ledgerTransactionRepository.save(transaction);

        PaymentFailedEvent failedEvent =
                new PaymentFailedEvent(
                        event.transaction_id(),
                        reason
                );

        String eventJson =
                objectMapper.writeValueAsString(failedEvent);

        kafkaTemplate.send(
                PAYMENT_FAILED_TOPIC,
                event.transaction_id(),
                eventJson
        );
    }
}