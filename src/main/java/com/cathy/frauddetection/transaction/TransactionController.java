package com.cathy.frauddetection.transaction;

import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
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

    // No annotation on criteria: Spring binds each record component from the
    // query string by name, so adding a filter means editing the record only.
    // Record binding goes through the canonical constructor, which needs the
    // -parameters compiler flag — already set by the Spring Boot parent POM.
    //
    // Pageable is resolved from page, size and sort by Spring Data Web.
    //
    // 200 with an empty content list, never 404: the collection resource exists,
    // it just has no elements under these filters.
    @GetMapping
    Page<TransactionResponse> search(TransactionSearchCriteria criteria, Pageable pageable) {
        return service.search(criteria, pageable);
    }
}