package com.cathy.frauddetection.rules;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import com.cathy.frauddetection.transaction.Transaction;
import com.cathy.frauddetection.transaction.TransactionStatus;
import org.drools.model.codegen.ExecutableModelProject;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.kie.api.KieServices;
import org.kie.api.builder.KieBuilder;
import org.kie.api.builder.KieFileSystem;
import org.kie.api.runtime.KieContainer;

// Package-private, same package as both evaluators: their constructors carry
// no access modifier, and this test bypasses Spring's @ConditionalOnProperty
// wiring entirely (it constructs both engines with `new`, which no single
// Spring context could do — only one of the two conditional beans can exist
// at a time).
class DualEngineComparisonTest {

    private static final long VELOCITY_LIMIT = 3;
    private static final String DRL_CLASSPATH = "rules/fraud-rules.drl";
    private static final String DRL_VIRTUAL_PATH =
            "src/main/resources/com/cathy/frauddetection/rules/fraud-rules.drl";

    private static KieContainer kieContainer;

    // Duplicates DroolsConfig.kieContainer() deliberately: Spring's conditional
    // wiring can never produce both evaluators in one context, so this test
    // must build the KieContainer itself, outside Spring, to get an instance
    // to hand to a manually constructed DroolsRuleEvaluator. If DroolsConfig's
    // build steps change, this copy needs updating too — a known cost of
    // sidestepping the conditional bean.
    @BeforeAll
    static void buildKieContainer() {
        KieServices kieServices = KieServices.get();
        KieFileSystem kieFileSystem = kieServices.newKieFileSystem();
        kieFileSystem.write(DRL_VIRTUAL_PATH,
                kieServices.getResources().newClassPathResource(DRL_CLASSPATH, StandardCharsets.UTF_8.name()));
        KieBuilder kieBuilder = kieServices.newKieBuilder(kieFileSystem).buildAll(ExecutableModelProject.class);

        if (kieBuilder.getResults().hasMessages(org.kie.api.builder.Message.Level.ERROR)) {
            throw new IllegalStateException("DRL compilation failed: " + kieBuilder.getResults().getMessages());
        }
        kieContainer = kieServices.newKieContainer(kieServices.getRepository().getDefaultReleaseId());
    }

    private final SimpleRuleEvaluator simpleEvaluator = new SimpleRuleEvaluator(VELOCITY_LIMIT);
    private final DroolsRuleEvaluator droolsEvaluator = new DroolsRuleEvaluator(kieContainer, VELOCITY_LIMIT);

    private Transaction transactionOf(BigDecimal amount, String country) {
        return new Transaction("TX-DUAL", "ACC-DUAL", amount, "EUR", country,
                "TRANSFER", Instant.now(), TransactionStatus.PENDING);
    }

    // Each row exercises a different combination of the three rules, including
    // the all-three-hit case where SimpleRuleEvaluator's own unit test already
    // proved clamp(120, 0, 100) = 100. This is the payoff of splitting the
    // weight 40 across two files (SimpleRuleEvaluator.WEIGHT and three DRL
    // literals): the only thing that can catch drift between them is a test
    // that runs the exact same input through both.
    @ParameterizedTest
    @CsvSource({
            "500.00,   IE, 0,  0",     // no rules hit
            "50000.00, IE, 0,  40",    // amount only
            "500.00,   IR, 0,  40",    // country only
            "500.00,   IE, 4,  40",    // velocity only
            "50000.00, IR, 4,  100"    // all three, clamped
    })
    void bothEnginesProduceIdenticalScores(String amount, String country, long velocityCount, int expectedScore) {
        Transaction transaction = transactionOf(new BigDecimal(amount), country);

        RuleResult simpleResult = simpleEvaluator.evaluate(transaction, velocityCount);
        RuleResult droolsResult = droolsEvaluator.evaluate(transaction, velocityCount);

        assertThat(simpleResult.riskScore()).isEqualTo(expectedScore);
        assertThat(droolsResult.riskScore())
                .as("Drools and Simple must agree on the score for the same input")
                .isEqualTo(simpleResult.riskScore());

        // Rule codes are compared too: fraud-rules.drl's rule names ("AMOUNT_THRESHOLD",
        // "HIGH_RISK_COUNTRY", "VELOCITY_LIMIT") were verified to match
        // SimpleRuleEvaluator's String constants character-for-character, so a
        // mismatch here would mean either side's rule naming had drifted.
        assertThat(droolsResult.hits()).hasSameSizeAs(simpleResult.hits());
        assertThat(droolsResult.hits())
                .extracting(RuleHit::ruleCode)
                .as("Drools and Simple must agree on which specific rules matched")
                .containsExactlyInAnyOrderElementsOf(
                        simpleResult.hits().stream().map(RuleHit::ruleCode).toList());
    }
}