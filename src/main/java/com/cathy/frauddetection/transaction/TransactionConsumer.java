package com.cathy.frauddetection.transaction;

import com.cathy.frauddetection.config.KafkaTopicConfig;
import com.cathy.frauddetection.rules.RuleEvaluator;
import com.cathy.frauddetection.rules.RuleResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import com.cathy.frauddetection.velocity.VelocityService;

@Component
class TransactionConsumer {

    private static final Logger log = LoggerFactory.getLogger(TransactionConsumer.class);

    private final TransactionRepository repository;

    // The interface, never SimpleRuleEvaluator. Phase 3 swaps in Drools
    // without touching this class — that is the whole point of the strategy.
    private final RuleEvaluator ruleEvaluator;
    private final VelocityService velocityService;

    TransactionConsumer(TransactionRepository repository,
                        RuleEvaluator ruleEvaluator,
                        VelocityService velocityService) {

        this.repository = repository;
        this.ruleEvaluator = ruleEvaluator;
        this.velocityService = velocityService;
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
                    long velocityCount = velocityService.recordAndCount(transaction.getAccountId());

                    // Orchestration only. This class decides nothing:
                    // the evaluator decides what matched, RuleResult.from decides
                    // the score, Decision.fromRiskScore decides the band.
                    RuleResult result = ruleEvaluator.evaluate(transaction,velocityCount);
                    Decision decision = Decision.fromRiskScore(result.riskScore());

                    transaction.setStatus(TransactionStatus.PROCESSED);
                    transaction.applyRiskAssessment(result.riskScore(), decision);

                    log.info("Processed transactionRef={} velocity={} score={} decision={} hits={}",
                            event.transactionRef(),velocityCount, result.riskScore(),decision,result.hits().size() );
                },
                () -> log.error("No row for transactionId={}", event.transactionId()));
    }
}