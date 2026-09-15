package com.swiftpay.transactiongateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class LedgerClientConfig {

    @Bean
    public RestClient ledgerRestClient(
            @Value("${ledger.service.url}") String ledgerServiceUrl
    ) {
        return RestClient.create(ledgerServiceUrl);
    }
}