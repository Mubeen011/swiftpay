package com.swiftpay.transactiongateway.event;

import java.math.BigDecimal;
import java.util.UUID;

public record PaymentInitiatedEvent(
        UUID payment_id,
        String sender_id,
        String receiver_id,
        BigDecimal amount,
        String currency,
        String transaction_id
) {
}