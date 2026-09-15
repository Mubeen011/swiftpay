package com.swiftpay.transactiongateway.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftpay.transactiongateway.dto.PaymentRequest;
import com.swiftpay.transactiongateway.dto.PaymentResponse;
import com.swiftpay.transactiongateway.entity.Payment;
import com.swiftpay.transactiongateway.event.PaymentInitiatedEvent;
import com.swiftpay.transactiongateway.repository.PaymentRepository;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Duration;

@Service
public class PaymentService {

    private static final String PAYMENT_INITIATED_TOPIC = "payment-initiated";
    private static final Duration IDEMPOTENCY_WINDOW = Duration.ofHours(24);
    private final RestClient ledgerRestClient;
    private final PaymentRepository paymentRepository;
    private final StringRedisTemplate redisTemplate;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public PaymentService(PaymentRepository paymentRepository,
                          StringRedisTemplate redisTemplate,
                          KafkaTemplate<String, String> kafkaTemplate,
                          ObjectMapper objectMapper,
                          RestClient ledgerRestClient)  {
        this.paymentRepository = paymentRepository;
        this.redisTemplate = redisTemplate;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.ledgerRestClient = ledgerRestClient;
    }

    @Transactional
    public PaymentResponse createPayment(PaymentRequest request) {

        String redisKey = "transaction:" + request.transaction_id();

        String existingPaymentId = redisTemplate.opsForValue().get(redisKey);

        if (existingPaymentId != null) {
            Payment existingPayment = paymentRepository
                    .findById(java.util.UUID.fromString(existingPaymentId))
                    .orElseThrow();

            return PaymentResponse.from(existingPayment);
        }

        Payment existingPayment = paymentRepository
                .findByTransactionId(request.transaction_id())
                .orElse(null);

        if (existingPayment != null) {

            redisTemplate.opsForValue().set(
                    redisKey,
                    existingPayment.getId().toString(),
                    IDEMPOTENCY_WINDOW
            );

            return PaymentResponse.from(existingPayment);
        }
        BigDecimal senderBalance = ledgerRestClient.get()
                .uri("/v1/accounts/{accountId}/balance", request.sender_id())
                .retrieve()
                .body(BigDecimal.class);

        if (senderBalance.compareTo(request.amount()) < 0) {
            throw new IllegalArgumentException("Insufficient funds");
        }

        Payment payment = new Payment(
                request.sender_id(),
                request.receiver_id(),
                request.amount(),
                request.currency(),
                request.transaction_id()
        );

        payment = paymentRepository.save(payment);

        PaymentInitiatedEvent event = new PaymentInitiatedEvent(
                payment.getId(),
                payment.getSenderId(),
                payment.getReceiverId(),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getTransactionId()
        );

        try {

            String eventJson = objectMapper.writeValueAsString(event);

            kafkaTemplate.send(
                    PAYMENT_INITIATED_TOPIC,
                    payment.getTransactionId(),
                    eventJson
            );

            redisTemplate.opsForValue().set(
                    redisKey,
                    payment.getId().toString(),
                    IDEMPOTENCY_WINDOW
            );

        } catch (JsonProcessingException e) {
            throw new IllegalStateException(
                    "Failed to create PaymentInitiated event",
                    e
            );
        }

        return PaymentResponse.from(payment);
    }
}