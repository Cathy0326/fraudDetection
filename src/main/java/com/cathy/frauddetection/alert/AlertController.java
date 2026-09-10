package com.cathy.frauddetection.alert;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/alerts")
class AlertController {

    // Fixed, not client-supplied: newest unreviewed first is the only order this
    // list needs, and id keeps paging stable when createdAt values tie.
    // Not offering the freedom means not having to defend it with a whitelist.
    private static final Sort FIXED_SORT =
            Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

    private static final int MAX_PAGE_SIZE = 100;

    private final AlertService alertService;

    AlertController(AlertService alertService) {
        this.alertService = alertService;
    }

    // status is required on purpose: no default means the caller cannot think
    // it is getting everything when it is getting one bucket.
    @GetMapping
    Page<AlertResponse> list(@RequestParam AlertStatus status,
                             @RequestParam(defaultValue = "0") int page,
                             @RequestParam(defaultValue = "20") int size) {
        // Truncate rather than reject: the response's own size field makes the
        // clamp visible, unlike silently returning fewer rows.
        int capped = Math.min(size, MAX_PAGE_SIZE);
        return alertService
                .findByStatus(status, PageRequest.of(page, capped, FIXED_SORT))
                .map(AlertResponse::from);
    }

    // Rejected here, at the boundary, so the client gets a 400. Alert.review()
    // keeps its own check: that one guards the invariant for every caller,
    // this one turns a client mistake into a status code.
    @PatchMapping("/{id}")
    AlertResponse review(@PathVariable Long id,
                         @Valid @RequestBody AlertReviewRequest request) {
        if (request.status() == AlertStatus.OPEN) {
            throw new InvalidReviewOutcomeException(request.status());
        }
        return AlertResponse.from(alertService.review(id, request.status()));
    }

    // Nested: this shape serves exactly one endpoint.
    record AlertReviewRequest(@NotNull AlertStatus status) {
    }
}