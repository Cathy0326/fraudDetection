package com.cathy.frauddetection.transaction;

import static org.assertj.core.api.Assertions.assertThat;

import com.cathy.frauddetection.AbstractIntegrationTest;
import com.cathy.frauddetection.alert.AlertStatus;
import jakarta.persistence.EntityManager;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

// Queries the real database's CHECK constraint definition instead of hardcoding
// a second copy of the allowed values in the test. A hand-copied list would
// only prove the test author remembered the same thing twice — it would not
// catch a migration and an enum drifting apart, which is exactly the failure
// mode this test exists to catch.
@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@ActiveProfiles("test")
class EnumSyncTest extends AbstractIntegrationTest {

    @org.springframework.beans.factory.annotation.Autowired
    private TestEntityManager testEntityManager;

    // Postgres rewrites "IN ('A', 'B')" internally to "= ANY (ARRAY['A', 'B'])",
    // so the constraint text cannot be assumed to contain a literal "IN (...)".
    // Extracting every single-quoted literal works regardless of which form
    // Postgres chose to store.
    private static final Pattern QUOTED_LITERAL = Pattern.compile("'([^']+)'");

    private List<String> allowedValuesFor(String constraintName) {
        EntityManager em = testEntityManager.getEntityManager();
        String definition = (String) em.createNativeQuery(
                        "SELECT pg_get_constraintdef(oid) FROM pg_constraint WHERE conname = :name")
                .setParameter("name", constraintName)
                .getSingleResult();

        Matcher matcher = QUOTED_LITERAL.matcher(definition);
        List<String> values = new java.util.ArrayList<>();
        while (matcher.find()) {
            values.add(matcher.group(1));
        }
        return values;
    }

    @Test
    void transactionStatusEnumMatchesDatabaseCheckConstraint() {
        List<String> databaseValues = allowedValuesFor("ck_transactions_status");
        List<String> enumValues = Arrays.stream(TransactionStatus.values())
                .map(Enum::name)
                .collect(Collectors.toList());

        assertThat(enumValues)
                .as("TransactionStatus.values() must match ck_transactions_status exactly")
                .containsExactlyInAnyOrderElementsOf(databaseValues);
    }

    @Test
    void decisionEnumMatchesDatabaseCheckConstraint() {
        List<String> databaseValues = allowedValuesFor("ck_transactions_decision");
        List<String> enumValues = Arrays.stream(Decision.values())
                .map(Enum::name)
                .collect(Collectors.toList());

        assertThat(enumValues)
                .as("Decision.values() must match ck_transactions_decision exactly")
                .containsExactlyInAnyOrderElementsOf(databaseValues);
    }

    @Test
    void alertStatusEnumMatchesDatabaseCheckConstraint() {
        List<String> databaseValues = allowedValuesFor("ck_alerts_status");
        List<String> enumValues = Arrays.stream(AlertStatus.values())
                .map(Enum::name)
                .collect(Collectors.toList());

        assertThat(enumValues)
                .as("AlertStatus.values() must match ck_alerts_status exactly")
                .containsExactlyInAnyOrderElementsOf(databaseValues);
    }

    @Test
    void alertDecisionCheckConstraintExcludesApprove() {
        // Not a parity check like the three tests above: ck_alerts_decision does NOT
        // allow every Decision value. An alert only exists for a non-approved
        // transaction, so APPROVE is deliberately excluded from the constraint.
        // Asserting "database subset of Decision.values()" would pass even if BLOCK
        // silently vanished from the constraint — pin the exact allowed set instead,
        // and separately confirm APPROVE still exists on the enum (if it's ever
        // removed, the "APPROVE is impossible here" comment above becomes stale).
        List<String> databaseValues = allowedValuesFor("ck_alerts_decision");

        assertThat(databaseValues)
                .as("ck_alerts_decision must allow exactly REVIEW and BLOCK")
                .containsExactlyInAnyOrder(Decision.REVIEW.name(), Decision.BLOCK.name());

        assertThat(Arrays.stream(Decision.values()).map(Enum::name))
                .as("Decision must still define APPROVE — if this fails, the exclusion "
                        + "comment on ck_alerts_decision is out of date")
                .contains(Decision.APPROVE.name());
    }
}