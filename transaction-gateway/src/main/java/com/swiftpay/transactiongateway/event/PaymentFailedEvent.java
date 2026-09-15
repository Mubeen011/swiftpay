package com.swiftpay.transactiongateway.event;

public record PaymentFailedEvent(
        String transaction_id,
        String reason
) {}