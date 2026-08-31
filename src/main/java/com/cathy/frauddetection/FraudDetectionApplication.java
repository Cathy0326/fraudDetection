package com.cathy.frauddetection;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

// Bundles three annotations: @Configuration, @EnableAutoConfiguration,
// @ComponentScan. Scanning starts at this class's package.
@SpringBootApplication
public class FraudDetectionApplication {

    public static void main(String[] args) {
        // Boots the Spring context and starts the embedded web server.
        SpringApplication.run(FraudDetectionApplication.class, args);
    }
}