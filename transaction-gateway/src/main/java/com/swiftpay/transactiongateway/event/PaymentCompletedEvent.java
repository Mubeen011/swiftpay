package com.swiftpay.transactiongateway.event;

import java.math.BigDecimal;

public record PaymentCompletedEvent(
        String transaction_id,
        String sender_id,
        String receiver_id,
        BigDecimal amount,
        String currency
) {}