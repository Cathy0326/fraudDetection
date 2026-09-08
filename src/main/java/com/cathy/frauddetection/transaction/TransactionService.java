package com.cathy.frauddetection.transaction;

import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransactionService {

    private static final Logger log = LoggerFactory.getLogger(TransactionService.class);

    // Entity field names, not column names. Anything outside this set is
    // rejected here so it never reaches Spring Data, which would throw
    // PropertyReferenceException from inside the framework and surface as 500.
    private static final Set<String> SORTABLE_FIELDS =
            Set.of("id", "amount", "riskScore", "occurredAt", "createdAt");

    private static final Sort DEFAULT_SORT = Sort.by(Sort.Order.desc("occurredAt"));

    // Appended to every sort. Paging runs two independent queries, and SQL makes
    // no promise about the relative order of rows with equal sort keys, so a
    // non-unique sort lets a row appear on two pages or on none. Direction is
    // arbitrary; uniqueness is the point.
    private static final Sort.Order TIE_BREAKER = Sort.Order.desc("id");

    private static final int MAX_PAGE_SIZE = 100;

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

    // public, not package-private: @Transactional works through an AOP proxy, and
    // on non-public methods it was silently ignored before Spring 6. It very
    // likely works now, but a silent no-op is not worth the saved keyword.
    //
    // readOnly = true: a Page issues two statements (rows, then COUNT). One
    // transaction gives both the same snapshot, so totalElements cannot disagree
    // with the rows returned.
    @Transactional(readOnly = true)
    public Page<TransactionResponse> search(TransactionSearchCriteria criteria, Pageable pageable) {
        validateRanges(criteria);

        return repository.findAll(TransactionSpecifications.from(criteria), applyPagingPolicy(pageable))
                // Page.map, not getContent().stream(): mapping the content list
                // alone would throw away totalElements and totalPages.
                .map(TransactionResponse::from);
    }

    private void validateRanges(TransactionSearchCriteria criteria) {
        // compareTo, never equals: BigDecimal.equals compares scale, so 10.0 and
        // 10.00 are unequal to it and equal to compareTo.
        if (criteria.minAmount() != null && criteria.maxAmount() != null
                && criteria.minAmount().compareTo(criteria.maxAmount()) > 0) {
            throw new InvalidSearchCriteriaException("minAmount must not be greater than maxAmount");
        }

        // Both are non-null here, so unboxing for > cannot throw.
        if (criteria.minRiskScore() != null && criteria.maxRiskScore() != null
                && criteria.minRiskScore() > criteria.maxRiskScore()) {
            throw new InvalidSearchCriteriaException("minRiskScore must not be greater than maxRiskScore");
        }

        if (criteria.occurredFrom() != null && criteria.occurredTo() != null
                && criteria.occurredFrom().isAfter(criteria.occurredTo())) {
            throw new InvalidSearchCriteriaException("occurredFrom must not be after occurredTo");
        }
    }

    private Pageable applyPagingPolicy(Pageable pageable) {
        for (Sort.Order order : pageable.getSort()) {
            if (!SORTABLE_FIELDS.contains(order.getProperty())) {
                throw new InvalidSearchCriteriaException("Cannot sort by: " + order.getProperty());
            }
        }

        Sort requested = pageable.getSort().isSorted() ? pageable.getSort() : DEFAULT_SORT;

        // Truncated, not rejected: an oversized page is a well-formed request we
        // are unwilling to serve in full, and the response reports the size it
        // actually used. A reversed range is different — no row can satisfy it,
        // and an empty page would look identical to "nothing matched".
        int size = Math.min(pageable.getPageSize(), MAX_PAGE_SIZE);

        return PageRequest.of(pageable.getPageNumber(), size, requested.and(Sort.by(TIE_BREAKER)));
    }
}