package com.cathy.frauddetection.transaction;

import com.cathy.frauddetection.config.KafkaTopicConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class TransactionConsumer {

    private static final Logger log = LoggerFactory.getLogger(TransactionConsumer.class);

    private final TransactionRepository repository;

    TransactionConsumer(TransactionRepository repository) {
        this.repository = repository;
    }

    // @Transactional works here: the call comes from Spring's listener container,
    // so it goes through the proxy. Not self-invocation.
    @KafkaListener(topics = KafkaTopicConfig.TRANSACTIONS_TOPIC)
    @Transactional
    void consume(TransactionEvent event) {
        repository.findById(event.transactionId()).ifPresentOrElse(
                transaction -> {
                    // Idempotency guard: Kafka is at-least-once, so this event
                    // may arrive twice. Re-processing must be a no-op.
                    if (transaction.getStatus() == TransactionStatus.PROCESSED) {
                        log.debug("Already processed, skipping ref={}", event.transactionRef());
                        return;
                    }
                    transaction.setStatus(TransactionStatus.PROCESSED);
                    log.info("Processed transactionRef={}", event.transactionRef());
                },
                () -> log.error("No row for transactionId={}", event.transactionId()));
    }
}