package com.swiftpay.transactiongateway.dto;

import com.swiftpay.transactiongateway.entity.Payment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentResponse(
        UUID payment_id,
        String sender_id,
        String receiver_id,
        BigDecimal amount,
        String currency,
        String transaction_id,
        String status,
        Instant created_at
) {

    public static PaymentResponse from(Payment payment) {
        return new PaymentResponse(
                payment.getId(),
                payment.getSenderId(),
                payment.getReceiverId(),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getTransactionId(),
                payment.getStatus().name(),
                payment.getCreatedAt()
        );
    }
}