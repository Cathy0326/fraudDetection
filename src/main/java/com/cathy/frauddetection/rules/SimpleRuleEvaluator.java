package com.cathy.frauddetection.rules;

import com.cathy.frauddetection.transaction.Transaction;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

// Default engine. matchIfMissing = true is load-bearing: if the property is
// absent the container must still get one RuleEvaluator, otherwise deleting a
// single config line breaks startup.
@ConditionalOnProperty(name = "fraud.rules.engine", havingValue = "simple", matchIfMissing = true)
@Component
class SimpleRuleEvaluator implements RuleEvaluator {

    // Codes are declared once. A typo can then only be wrong in one place,
    // which is NOT the same as compile-time safety.
    private static final String CODE_AMOUNT_THRESHOLD = "AMOUNT_THRESHOLD";
    private static final String CODE_HIGH_RISK_COUNTRY = "HIGH_RISK_COUNTRY";

    // Equal weights on purpose: no historical fraud data justifies ranking one
    // signal above another. 40 maps 1 hit -> REVIEW, 2 hits -> BLOCK.
    private static final int WEIGHT = 40;

    // String constructor, never BigDecimal.valueOf(double). The double literal
    // loses precision before valueOf is ever called.
    private static final BigDecimal AMOUNT_THRESHOLD = new BigDecimal("10000");

    // ISO 3166-1 alpha-2. Set, not List: this is membership, not a sequence.
    private static final Set<String> HIGH_RISK_COUNTRIES = Set.of("IR", "KP", "SY", "CU");

    @Override
    public RuleResult evaluate(Transaction transaction) {
        List<RuleHit> hits = new ArrayList<>();

        if (exceedsAmountThreshold(transaction)) {
            hits.add(new RuleHit(CODE_AMOUNT_THRESHOLD, WEIGHT));
        }
        if (isHighRiskCountry(transaction)) {
            hits.add(new RuleHit(CODE_HIGH_RISK_COUNTRY, WEIGHT));
        }

        // No summing, no clamping here. RuleResult.from owns the score.
        return RuleResult.from(hits);
    }

    // compareTo, never equals: equals compares scale too, so 10000 and 10000.0000
    // are not equal even though they are the same number.
    private boolean exceedsAmountThreshold(Transaction transaction) {
        return transaction.getAmount().compareTo(AMOUNT_THRESHOLD) > 0;
    }

    private boolean isHighRiskCountry(Transaction transaction) {
        return HIGH_RISK_COUNTRIES.contains(transaction.getDestinationCountry());
    }
}