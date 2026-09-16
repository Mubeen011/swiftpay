package com.swiftpay.ledgerservice.controller;

import com.swiftpay.ledgerservice.entity.LedgerTransaction;
import com.swiftpay.ledgerservice.repository.LedgerTransactionRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/v1/transactions")
public class TransactionController {

    private final LedgerTransactionRepository repository;

    public TransactionController(
            LedgerTransactionRepository repository
    ) {
        this.repository = repository;
    }

    @GetMapping("/{transactionId}")
    public ResponseEntity<LedgerTransaction> getTransaction(
            @PathVariable String transactionId
    ) {
        return repository.findByTransactionId(transactionId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<LedgerTransaction>> getTransactionHistory(
            @PathVariable String userId
    ) {
        return ResponseEntity.ok(
                repository.findBySenderIdOrReceiverId(userId, userId)
        );
    }
}