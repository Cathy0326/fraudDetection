package com.cathy.frauddetection.transaction;

import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/transactions")
class TransactionController {

    private final TransactionService service;

    TransactionController(TransactionService service) {
        this.service = service;
    }

    @PostMapping
    ResponseEntity<Void> submit(@Valid @RequestBody TransactionRequest request) {
        Long id = service.submit(request);
        return ResponseEntity.accepted()
                .location(URI.create("/api/v1/transactions/" + id))
                .build();
    }
}