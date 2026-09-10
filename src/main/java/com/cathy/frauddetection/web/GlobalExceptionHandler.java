package com.cathy.frauddetection.web;

import com.cathy.frauddetection.alert.InvalidReviewOutcomeException;
import com.cathy.frauddetection.transaction.DuplicateTransactionException;
import com.cathy.frauddetection.transaction.InvalidSearchCriteriaException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import com.cathy.frauddetection.alert.AlertNotFoundException;
import java.util.Arrays;
import java.util.stream.Collectors;
import org.springframework.validation.BindException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

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

    // 400: the body parses and the enum value exists, but OPEN is a starting
    // state, not something a review can produce. Client's mistake, so no stack.
    @ExceptionHandler(InvalidReviewOutcomeException.class)
    ProblemDetail handleInvalidReviewOutcome(InvalidReviewOutcomeException exception) {
        log.warn("Rejected review: {}", exception.getMessage());

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, exception.getMessage());
        problem.setTitle("Invalid review outcome");
        return problem;
    }

    // 400: the query string names a real parameter, but the value cannot become
// the parameter's type (e.g. status=WRONG when status is an AlertStatus).
// This fires before our own code runs — Spring's binder rejects the request
// during argument resolution, for simple @RequestParam bindings only.
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ProblemDetail handleTypeMismatch(MethodArgumentTypeMismatchException exception) {
        log.warn("Rejected parameter: {}", exception.getMessage());

        String detail = "Parameter '" + exception.getName() + "' has invalid value '"
                + exception.getValue() + "'";
        Class<?> requiredType = exception.getRequiredType();
        if (requiredType != null && requiredType.isEnum()) {
            detail += "; must be one of " + Arrays.toString(requiredType.getEnumConstants());
        }

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
        problem.setTitle("Invalid parameter");
        return problem;
    }

    // 400: TransactionSearchCriteria is bound as one object (no @RequestParam on
// individual fields — see its javadoc), so Spring collects every field
// conversion failure into one BindException instead of throwing on the first.
// All failures are reported together: a client who mistyped two filters at
// once should not have to fix them one request at a time.
    @ExceptionHandler(BindException.class)
    ProblemDetail handleBindFailure(BindException exception) throws BindException {
        if(exception instanceof MethodArgumentNotValidException){
            throw exception;
    }
        log.warn("Rejected search binding: {}", exception.getMessage());

        String detail = exception.getFieldErrors().stream()
                .map(error -> error.getField() + " has invalid value '"
                        + error.getRejectedValue() + "'")
                .collect(Collectors.joining("; "));

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
        problem.setTitle("Invalid search parameter");
        return problem;
    }

    // 400: the JSON is syntactically valid but a field's value cannot become its
// target type — e.g. {"status":"WRONG"} for an AlertStatus. Distinct from
// InvalidReviewOutcomeException: that one fires after the body parses
// successfully and rejects a real enum value (OPEN) for business reasons;
// this one fires when the value never became a valid AlertStatus at all.
// Detail is a fixed string, not exception.getMessage() — Jackson's message
// here includes internal class/field paths, and InvalidSearchCriteriaException's
// rule applies equally: only echo values the client already sent, never our
// own internals.
    @ExceptionHandler(HttpMessageNotReadableException.class)
    ProblemDetail handleUnreadableBody(HttpMessageNotReadableException exception) {
        log.warn("Rejected request body: {}", exception.getMessage());

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "Request body could not be parsed");
        problem.setTitle("Malformed request body");
        return problem;
    }
}