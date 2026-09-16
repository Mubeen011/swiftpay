package com.swiftpay.ledgerservice.event;

public record PaymentFailedEvent(
        String transaction_id,
        String reason
) {
}