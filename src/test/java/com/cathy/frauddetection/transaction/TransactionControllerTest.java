package com.cathy.frauddetection.transaction;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

// @Import pulls in GlobalExceptionHandler: it lives in a different package
// (web, not transaction), and @WebMvcTest only auto-detects @ControllerAdvice
// classes in the same package as the controller under test and its
// subpackages by default in some configurations — importing it explicitly
// removes any doubt about whether it's on the classpath for this slice.
@WebMvcTest(TransactionController.class)
class TransactionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    // TransactionService is not a Web component, so @WebMvcTest never
    // constructs a real one. @MockitoBean replaces it in the context with a
    // Mockito mock — this class tests the controller/exception-handler wiring,
    // never the service's actual search or submit logic.
    @MockitoBean
    private TransactionService service;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private String validRequestJson() throws Exception {
        return """
                {
                  "transactionRef": "TX-CTRL-1",
                  "accountId": "ACC-CTRL",
                  "amount": 500.00,
                  "currency": "EUR",
                  "destinationCountry": "IE",
                  "transactionType": "TRANSFER",
                  "occurredAt": "%s"
                }
                """.formatted(Instant.now());
    }

    @Test
    void submitReturns202WithLocationHeader() throws Exception {
        when(service.submit(any())).thenReturn(42L);

        mockMvc.perform(post("/api/v1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestJson()))
                .andExpect(status().isAccepted())
                .andExpect(header().string("Location", "/api/v1/transactions/42"));
    }

    // Confirms DuplicateTransactionException maps to 409 with the RFC 9457
    // shape, not the 500 this project's Phase 1 originally shipped with.
    @Test
    void submitDuplicateReturns409WithProblemDetail() throws Exception {
        when(service.submit(any()))
                .thenThrow(new DuplicateTransactionException("TX-CTRL-1"));

        mockMvc.perform(post("/api/v1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestJson()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title", is("Transaction already exists")))
                .andExpect(jsonPath("$.detail", is("Transaction already exists: TX-CTRL-1")));
    }

    @Test
    void searchWithInvalidCriteriaReturns400WithProblemDetail() throws Exception {
        when(service.search(any(), any()))
                .thenThrow(new InvalidSearchCriteriaException("Cannot sort by: currency"));

        mockMvc.perform(get("/api/v1/transactions").param("sort", "currency,desc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title", is("Invalid search criteria")))
                .andExpect(jsonPath("$.detail", is("Cannot sort by: currency")));
    }

    @Test
    void searchReturns200WithPageBody() throws Exception {
        when(service.search(any(), any()))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(java.util.List.of()));

        mockMvc.perform(get("/api/v1/transactions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", is(0)));
    }

    // Locks in a fact your own handoff notes already recorded: Bean Validation
    // failures never reach GlobalExceptionHandler. Spring's default handling
    // answers first, so this response 400 without a "title" field shaped like
    // the ones the other handlers produce. If someone later adds a handler for
    // MethodArgumentNotValidException, this test will fail and force a
    // deliberate look at the new response shape instead of a silent change.
    @Test
    void malformedRequestBodyBypassesGlobalExceptionHandler() throws Exception {
        String invalidCountry = """
                {
                  "transactionRef": "TX-CTRL-2",
                  "accountId": "ACC-CTRL",
                  "amount": 500.00,
                  "currency": "EUR",
                  "destinationCountry": "ie",
                  "transactionType": "TRANSFER",
                  "occurredAt": "%s"
                }
                """.formatted(Instant.now());

        mockMvc.perform(post("/api/v1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidCountry))
                .andExpect(status().isBadRequest())
                // Spring's default validation error body carries no "title"
                // field — GlobalExceptionHandler's ProblemDetail responses do.
                // Its absence here is the assertion.
                .andExpect(jsonPath("$.title").doesNotExist());
    }
}