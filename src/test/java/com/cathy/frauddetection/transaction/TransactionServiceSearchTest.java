package com.cathy.frauddetection.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.cathy.frauddetection.AbstractIntegrationTest;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@ActiveProfiles("test")
class TransactionServiceSearchTest extends AbstractIntegrationTest {

    @Autowired
    private TransactionRepository repository;

    @Autowired
    private TestEntityManager entityManager;

    private TransactionService service;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        service = new TransactionService(repository, mock(TransactionProducer.class));
    }

    private Transaction persist(String accountId, BigDecimal amount, String country, Instant occurredAt) {
        Transaction transaction = new Transaction(
                "TX-" + accountId + "-" + System.nanoTime(), accountId, amount,
                "EUR", country, "TRANSFER", occurredAt, TransactionStatus.PENDING);
        return repository.save(transaction);
    }

    private static TransactionSearchCriteria emptyCriteria() {
        return new TransactionSearchCriteria(null, null, null, null, null, null, null, null, null, null);
    }

    @Test
    void defaultSortIsAppliedWhenNoSortRequested() {
        Instant now = Instant.now();
        persist("ACC-A", new BigDecimal("100.00"), "IE", now.minus(2, ChronoUnit.DAYS));
        persist("ACC-B", new BigDecimal("100.00"), "IE", now);
        entityManager.flush();
        entityManager.clear();

        Page<TransactionResponse> page = service.search(emptyCriteria(), PageRequest.of(0, 10));

        // DEFAULT_SORT is occurredAt DESC: the most recent row must come first
        // even though no sort parameter was requested.
        assertThat(page.getContent().get(0).accountId()).isEqualTo("ACC-B");
    }

    @Test
    void pageSizeIsTruncatedToMaximum() {
        persist("ACC-A", new BigDecimal("100.00"), "IE", Instant.now());
        entityManager.flush();
        entityManager.clear();

        // MAX_PAGE_SIZE is 100. Requesting 500 must not throw and must not be
        // served in full — the response reports the size it actually used, so
        // the truncation is observable rather than silent.
        Page<TransactionResponse> page = service.search(emptyCriteria(), PageRequest.of(0, 500));

        assertThat(page.getSize()).isEqualTo(100);
    }

    // The scenario the TIE_BREAKER exists to prevent: rows with an identical
    // primary sort key, paginated across two independent queries. Checking a
    // single page can pass by coincidence even without a tie-breaker — only
    // checking that two pages together cover every row exactly once, with no
    // duplicate and no gap, actually proves the tie-breaker is doing something.
    @Test
    void tieBreakerPreventsDuplicateOrMissingRowsAcrossPages() {
        Instant sameInstant = Instant.now();
        Transaction t1 = persist("ACC-TIE", new BigDecimal("100.00"), "IE", sameInstant);
        Transaction t2 = persist("ACC-TIE", new BigDecimal("200.00"), "IE", sameInstant);
        Transaction t3 = persist("ACC-TIE", new BigDecimal("300.00"), "IE", sameInstant);
        entityManager.flush();
        entityManager.clear();

        Page<TransactionResponse> page0 = service.search(emptyCriteria(), PageRequest.of(0, 2));
        Page<TransactionResponse> page1 = service.search(emptyCriteria(), PageRequest.of(1, 2));

        List<Long> allIds = java.util.stream.Stream
                .concat(page0.getContent().stream(), page1.getContent().stream())
                .map(TransactionResponse::id)
                .collect(Collectors.toList());

        assertThat(allIds)
                .as("every row appears exactly once across both pages, none duplicated or dropped")
                .containsExactlyInAnyOrder(t1.getId(), t2.getId(), t3.getId());
    }

    @Test
    void sortingByWhitelistedFieldIsHonoured() {
        persist("ACC-A", new BigDecimal("500.00"), "IE", Instant.now());
        persist("ACC-B", new BigDecimal("100.00"), "IE", Instant.now());
        entityManager.flush();
        entityManager.clear();

        Page<TransactionResponse> page = service.search(emptyCriteria(),
                PageRequest.of(0, 10, Sort.by(Sort.Order.asc("amount"))));

        assertThat(page.getContent())
                .extracting(TransactionResponse::amount)
                .isSorted();
    }

    @Test
    void sortingByFieldOutsideWhitelistIsRejected() {
        var pageable = PageRequest.of(0, 10, Sort.by(Sort.Order.desc("currency")));

        // "currency" is a real entity field but not in SORTABLE_FIELDS, so this
        // must be rejected here — before it reaches Spring Data and comes back
        // as an unhandled PropertyReferenceException surfacing as a 500.
        assertThatThrownBy(() -> service.search(emptyCriteria(), pageable))
                .isInstanceOf(InvalidSearchCriteriaException.class)
                .hasMessageContaining("currency");
    }

    @Test
    void reversedAmountRangeIsRejected() {
        var criteria = new TransactionSearchCriteria(null, null, null, null,
                new BigDecimal("500.00"), new BigDecimal("100.00"), null, null, null, null);

        assertThatThrownBy(() -> service.search(criteria, PageRequest.of(0, 10)))
                .isInstanceOf(InvalidSearchCriteriaException.class);
    }

    @Test
    void reversedRiskScoreRangeIsRejected() {
        var criteria = new TransactionSearchCriteria(null, null, null, null,
                null, null, (short) 80, (short) 20, null, null);

        assertThatThrownBy(() -> service.search(criteria, PageRequest.of(0, 10)))
                .isInstanceOf(InvalidSearchCriteriaException.class);
    }

    @Test
    void reversedOccurredRangeIsRejected() {
        Instant now = Instant.now();
        var criteria = new TransactionSearchCriteria(null, null, null, null,
                null, null, null, null, now, now.minus(1, ChronoUnit.DAYS));

        assertThatThrownBy(() -> service.search(criteria, PageRequest.of(0, 10)))
                .isInstanceOf(InvalidSearchCriteriaException.class);
    }

    // Confirms the null-preserving mapping survives the full path from entity
    // through Specification through Page.map — not just that
    // TransactionResponse.from() handles null in isolation.
    @Test
    void unscoredTransactionMapsToNullRiskScoreAndDecisionInResponse() {
        persist("ACC-UNSCORED", new BigDecimal("100.00"), "IE", Instant.now());
        entityManager.flush();
        entityManager.clear();

        Page<TransactionResponse> page = service.search(emptyCriteria(), PageRequest.of(0, 10));

        assertThat(page.getContent().get(0).riskScore()).isNull();
        assertThat(page.getContent().get(0).decision()).isNull();
    }

    @Test
    void filteringByAccountIdReportsCorrectTotalElements() {
        persist("ACC-FILTER", new BigDecimal("100.00"), "IE", Instant.now());
        persist("ACC-FILTER", new BigDecimal("200.00"), "IE", Instant.now());
        persist("ACC-OTHER", new BigDecimal("300.00"), "IE", Instant.now());
        entityManager.flush();
        entityManager.clear();

        var criteria = new TransactionSearchCriteria("ACC-FILTER", null, null, null,
                null, null, null, null, null, null);
        Page<TransactionResponse> page = service.search(criteria, PageRequest.of(0, 10));

        // totalElements comes from a separate COUNT query. If it disagreed with
        // the number of rows actually returned, that would mean the two queries
        // saw different snapshots — exactly what readOnly transactions in
        // production exist to prevent.
        assertThat(page.getTotalElements()).isEqualTo(2);
    }
}