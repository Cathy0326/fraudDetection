package com.cathy.frauddetection.transaction;

import com.cathy.frauddetection.config.KafkaTopicConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
class TransactionProducer {

    private static final Logger log = LoggerFactory.getLogger(TransactionProducer.class);

    private final KafkaTemplate<String, TransactionEvent> kafkaTemplate;

    TransactionProducer(KafkaTemplate<String, TransactionEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    void publish(TransactionEvent event) {
        // Key = accountId: all events for one account land on the same partition,
        // so they are consumed in order. Velocity detection depends on this.
        kafkaTemplate.send(KafkaTopicConfig.TRANSACTIONS_TOPIC, event.accountId(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        // The row stays PENDING. Detectable, not recoverable.
                        log.error("Failed to publish transactionRef={}", event.transactionRef(), ex);
                    } else {
                        log.debug("Published transactionRef={} to partition={}",
                                event.transactionRef(),
                                result.getRecordMetadata().partition());
                    }
                });
    }
}