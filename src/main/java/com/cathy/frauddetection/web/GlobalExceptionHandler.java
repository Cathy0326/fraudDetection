package com.cathy.frauddetection.web;

import com.cathy.frauddetection.transaction.DuplicateTransactionException;
import com.cathy.frauddetection.transaction.InvalidSearchCriteriaException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import com.cathy.frauddetection.alert.AlertNotFoundException;

/**
 * Maps application exceptions to RFC 9457 problem responses.
 *
 * Its own package: exception handling belongs to no single feature, and this
 * will take on other packages' exceptions later.
 *
 * There is deliberately no handler for Exception. A catch-all would turn every
 * unknown failure into a tidy JSON body and cost us the stack trace that Spring
 * prints when nothing handles it.
 */
@RestControllerAdvice
class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // WARN and no stack trace: a rejected query string is the client's mistake,
    // not a failure of ours. Stack traces here would bury the real ones.
    @ExceptionHandler(InvalidSearchCriteriaException.class)
    ProblemDetail handleInvalidSearchCriteria(InvalidSearchCriteriaException exception) {
        log.warn("Rejected search: {}", exception.getMessage());

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, exception.getMessage());
        problem.setTitle("Invalid search criteria");
        return problem;
    }

    // 409, not 500: the request is well formed and the server is healthy — the
    // resource simply already exists. 500 told the client to retry, which could
    // never succeed.
    @ExceptionHandler(DuplicateTransactionException.class)
    ProblemDetail handleDuplicateTransaction(DuplicateTransactionException exception) {
        log.warn("Rejected duplicate: {}", exception.getMessage());

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT, exception.getMessage());
        problem.setTitle("Transaction already exists");
        return problem;
    }

    // 404, not 400: the id is a well-formed long, it just points at nothing.
    // A 400 would tell the client to fix its request format, which is already fine.
    @ExceptionHandler(AlertNotFoundException.class)
    ProblemDetail handleAlertNotFound(AlertNotFoundException exception) {
        log.warn("Review target missing: {}", exception.getMessage());

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND, exception.getMessage());
        problem.setTitle("Alert not found");
        return problem;
    }
}