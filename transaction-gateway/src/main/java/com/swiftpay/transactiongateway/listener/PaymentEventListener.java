package com.swiftpay.transactiongateway.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftpay.transactiongateway.event.PaymentCompletedEvent;
import com.swiftpay.transactiongateway.event.PaymentFailedEvent;
import com.swiftpay.transactiongateway.entity.Payment;
import com.swiftpay.transactiongateway.entity.PaymentStatus;
import com.swiftpay.transactiongateway.repository.PaymentRepository;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class PaymentEventListener {

    private final PaymentRepository paymentRepository;
    private final ObjectMapper objectMapper;

    public PaymentEventListener(
            PaymentRepository paymentRepository,
            ObjectMapper objectMapper
    ) {
        this.paymentRepository = paymentRepository;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topics = "payment-completed",
            groupId = "transaction-gateway"
    )
    @Transactional
    public void handlePaymentCompleted(String message) {
        try {
            PaymentCompletedEvent event =
                    objectMapper.readValue(message, PaymentCompletedEvent.class);

            paymentRepository.findByTransactionId(event.transaction_id())
                    .ifPresent(payment -> {
                        payment.setStatus(PaymentStatus.COMPLETED);
                        paymentRepository.save(payment);
                    });

        } catch (Exception e) {
            throw new IllegalStateException(
                    "Failed to process PaymentCompleted event",
                    e
            );
        }
    }

    @KafkaListener(
            topics = "payment-failed",
            groupId = "transaction-gateway"
    )
    @Transactional
    public void handlePaymentFailed(String message) {
        try {
            PaymentFailedEvent event =
                    objectMapper.readValue(message, PaymentFailedEvent.class);

            paymentRepository.findByTransactionId(event.transaction_id())
                    .ifPresent(payment -> {
                        payment.setStatus(PaymentStatus.FAILED);
                        paymentRepository.save(payment);
                    });

        } catch (Exception e) {
            throw new IllegalStateException(
                    "Failed to process PaymentFailed event",
                    e
            );
        }
    }
}