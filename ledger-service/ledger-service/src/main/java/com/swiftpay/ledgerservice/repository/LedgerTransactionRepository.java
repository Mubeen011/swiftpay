package com.swiftpay.ledgerservice.repository;

import com.swiftpay.ledgerservice.entity.LedgerTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;
import java.util.List;

public interface LedgerTransactionRepository
        extends JpaRepository<LedgerTransaction, UUID> {

    Optional<LedgerTransaction> findByTransactionId(String transactionId);

    List<LedgerTransaction> findBySenderIdOrReceiverId(
            String senderId,
            String receiverId
    );
}