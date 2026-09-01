package com.cathy.frauddetection.transaction;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Inbound HTTP contract. Separate from both the entity and the event:
 * what a client may send is not what we store, nor what we publish.
 */
public record TransactionRequest(

        @NotBlank @Size(max = 64)
        String transactionRef,

        @NotBlank @Size(max = 64)
        String accountId,

        // DecimalMin over @Positive: inclusive=false rejects zero explicitly,
        // and Digits mirrors NUMERIC(19,4) so oversized values fail at the edge.
        @NotNull @DecimalMin(value = "0.0", inclusive = false) @Digits(integer = 15, fraction = 4)
        BigDecimal amount,

        @NotBlank @Size(min = 3, max = 3)
        String currency,

        @NotBlank @Size(min = 2, max = 2)
        String destinationCountry,

        @NotBlank @Size(max = 20)
        String transactionType,

        @NotNull @PastOrPresent
        Instant occurredAt
) {
}