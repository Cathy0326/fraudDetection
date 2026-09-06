package com.cathy.frauddetection.transaction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class TransactionService {

    private static final Logger log = LoggerFactory.getLogger(TransactionService.class);

    private final TransactionRepository repository;
    private final TransactionProducer producer;

    TransactionService(TransactionRepository repository, TransactionProducer producer) {
        this.repository = repository;
        this.producer = producer;
    }

    // No @Transactional here on purpose: repository.save() commits on its own,
    // so the row is visible before the event is published.
    public Long submit(TransactionRequest request) {
        if (repository.existsByTransactionRef(request.transactionRef())) {
            throw new DuplicateTransactionException(request.transactionRef());
        }

        Transaction saved = repository.save(new Transaction(
                request.transactionRef(),
                request.accountId(),
                request.amount(),
                request.currency(),
                request.destinationCountry(),
                request.transactionType(),
                request.occurredAt(),
                TransactionStatus.PENDING));

        producer.publish(new TransactionEvent(
                saved.getId(),
                saved.getTransactionRef(),
                saved.getAccountId(),
                saved.getAmount(),
                saved.getCurrency(),
                saved.getDestinationCountry(),
                saved.getOccurredAt()));

        log.info("Submitted transactionRef={} id={}", saved.getTransactionRef(), saved.getId());
        return saved.getId();
    }
}