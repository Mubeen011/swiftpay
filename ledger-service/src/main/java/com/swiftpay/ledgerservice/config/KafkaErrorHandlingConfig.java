package com.swiftpay.ledgerservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class KafkaErrorHandlingConfig {

    @Bean
    public DefaultErrorHandler kafkaErrorHandler() {
        // Initial attempt + 4 retries, with 2 seconds between retries.
        return new DefaultErrorHandler(
                new FixedBackOff(2000L, 4L)
        );
    }
}