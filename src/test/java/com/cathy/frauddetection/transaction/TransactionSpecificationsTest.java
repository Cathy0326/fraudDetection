package com.cathy.frauddetection.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import com.cathy.frauddetection.AbstractIntegrationTest;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace;
import org.springframework.test.context.ActiveProfiles;

// Replace.NONE is load-bearing: without it, @DataJpaTest swaps in an
// embedded database and the Postgres container from AbstractIntegrationTest
// is never touched. An assertion passing on H2 proves nothing about how
// Postgres actually evaluates NULL comparisons or case-sensitive equality.
@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@ActiveProfiles("test")
class TransactionSpecificationsTest extends AbstractIntegrationTest {

    @Autowired
    private TransactionRepository repository;

    // Gives direct access to flush()/clear(), which repository.save() alone
    // does not guarantee. Without an explicit flush + clear, a query issued
    // right after save() can be answered from Hibernate's first-level cache
    // instead of a real round trip to Postgres — an assertion could pass
    // while testing nothing about the generated SQL.
    @Autowired
    private TestEntityManager entityManager;

    private Transaction persist(String accountId, BigDecimal amount, String country) {
        Transaction transaction = new Transaction(
                "TX-" + accountId + "-" + System.nanoTime(), accountId, amount,
                "EUR", country, "TRANSFER", Instant.now(), TransactionStatus.PENDING);
        return repository.save(transaction);
    }

    // Test-only builder. TransactionSearchCriteria is a 10-field record with
    // no builder in production code — Spring's data binder uses the full
    // constructor directly, so a builder there would be dead weight. Tests
    // need readability the binder doesn't, hence a local, test-scoped one.
    private static final class CriteriaBuilder {
        private String accountId;
        private String destinationCountry;
        private TransactionStatus status;
        private Decision decision;
        private BigDecimal minAmount;
        private BigDecimal maxAmount;
        private Short minRiskScore;
        private Short maxRiskScore;
        private Instant occurredFrom;
        private Instant occurredTo;

        CriteriaBuilder accountId(String v) { this.accountId = v; return this; }
        CriteriaBuilder destinationCountry(String v) { this.destinationCountry = v; return this; }
        CriteriaBuilder minAmount(String v) { this.minAmount = new BigDecimal(v); return this; }
        CriteriaBuilder maxAmount(String v) { this.maxAmount = new BigDecimal(v); return this; }
        CriteriaBuilder minRiskScore(int v) { this.minRiskScore = (short) v; return this; }

        TransactionSearchCriteria build() {
            return new TransactionSearchCriteria(accountId, destinationCountry, status, decision,
                    minAmount, maxAmount, minRiskScore, maxRiskScore, occurredFrom, occurredTo);
        }
    }

    @Test
    void filtersByAccountId() {
        persist("ACC-MATCH", new BigDecimal("500.00"), "IE");
        persist("ACC-OTHER", new BigDecimal("500.00"), "IE");
        entityManager.flush();
        entityManager.clear();

        var criteria = new CriteriaBuilder().accountId("ACC-MATCH").build();
        List<Transaction> result = repository.findAll(TransactionSpecifications.from(criteria));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getAccountId()).isEqualTo("ACC-MATCH");
    }

    @Test
    void filtersByAmountRange() {
        persist("ACC-RANGE", new BigDecimal("100.00"), "IE");   // below range
        Transaction inRange = persist("ACC-RANGE", new BigDecimal("500.00"), "IE");
        persist("ACC-RANGE", new BigDecimal("900.00"), "IE");   // above range
        entityManager.flush();
        entityManager.clear();

        var criteria = new CriteriaBuilder()
                .accountId("ACC-RANGE")
                .minAmount("200.00")
                .maxAmount("800.00")
                .build();
        List<Transaction> result = repository.findAll(TransactionSpecifications.from(criteria));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(inRange.getId());
    }

    // The behaviour this test locks in comes from SQL's three-valued logic,
    // not from anything TransactionSpecifications does explicitly. NULL >= 0
    // evaluates to UNKNOWN, and WHERE only keeps rows where the condition is
    // TRUE — so an unscored row is silently excluded from any risk-score
    // range filter. Worth pinning precisely because nothing in the code says
    // this out loud; it's an emergent property of the database, not a rule
    // anyone wrote.
    @Test
    void excludesUnscoredRowsWhenFilteringByRiskScore() {
        Transaction scored = persist("ACC-SCORED", new BigDecimal("500.00"), "IE");
        scored.applyRiskAssessment(40, Decision.REVIEW);
        repository.save(scored);
        persist("ACC-UNSCORED", new BigDecimal("500.00"), "IE"); // riskScore stays null
        entityManager.flush();
        entityManager.clear();

        var criteria = new CriteriaBuilder().minRiskScore(0).build();
        List<Transaction> result = repository.findAll(TransactionSpecifications.from(criteria));

        assertThat(result)
                .extracting(Transaction::getAccountId)
                .containsExactly("ACC-SCORED");
    }

    // Documents a known gap rather than a bug: the entity stores whatever
    // TransactionRequest's @Pattern already forced to uppercase, and the
    // Specification does no case normalisation on top. This test exists so
    // that if someone later adds .toUpperCase() here, it fails loudly and
    // forces a deliberate decision about the change — not a silent one.
    @Test
    void countryFilterIsCaseSensitive() {
        persist("ACC-CASE", new BigDecimal("500.00"), "IR");
        entityManager.flush();
        entityManager.clear();

        var criteria = new CriteriaBuilder().destinationCountry("ir").build();
        List<Transaction> result = repository.findAll(TransactionSpecifications.from(criteria));

        assertThat(result).isEmpty();
    }

    @Test
    void emptyCriteriaReturnsAllRows() {
        persist("ACC-A", new BigDecimal("100.00"), "IE");
        persist("ACC-B", new BigDecimal("200.00"), "IE");
        entityManager.flush();
        entityManager.clear();

        var criteria = new CriteriaBuilder().build();
        List<Transaction> result = repository.findAll(TransactionSpecifications.from(criteria));

        // Relies on @DataJpaTest's per-method transaction rollback: without
        // it, rows from other test methods or classes sharing this container
        // would leak in and this count would be unpredictable.
        assertThat(result).hasSize(2);
    }

    @Test
    void combinesMultipleFiltersWithAnd() {
        persist("ACC-AND", new BigDecimal("50.00"), "IE");     // wrong amount, right account
        persist("ACC-OTHER", new BigDecimal("500.00"), "IE");  // right amount, wrong account
        Transaction matchesBoth = persist("ACC-AND", new BigDecimal("500.00"), "IE");
        entityManager.flush();
        entityManager.clear();

        var criteria = new CriteriaBuilder()
                .accountId("ACC-AND")
                .minAmount("400.00")
                .build();
        List<Transaction> result = repository.findAll(TransactionSpecifications.from(criteria));

        // If reduce(Specification::and) ever regressed to picking only the
        // last filter, or silently became an OR, this would return 2 or 3
        // rows instead of exactly the one satisfying both conditions.
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(matchesBoth.getId());
    }
}