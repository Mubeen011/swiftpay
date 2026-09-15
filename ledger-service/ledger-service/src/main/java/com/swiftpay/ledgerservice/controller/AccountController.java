package com.swiftpay.ledgerservice.controller;

import com.swiftpay.ledgerservice.entity.Account;
import com.swiftpay.ledgerservice.repository.AccountRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequestMapping("/v1/accounts")
public class AccountController {

    private final AccountRepository accountRepository;

    public AccountController(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @GetMapping("/{accountId}/balance")
    public ResponseEntity<BigDecimal> getBalance(
            @PathVariable String accountId
    ) {
        return accountRepository.findByAccountId(accountId)
                .map(account -> ResponseEntity.ok(account.getBalance()))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}