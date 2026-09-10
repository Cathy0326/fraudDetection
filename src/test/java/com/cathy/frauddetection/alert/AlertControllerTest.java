package com.cathy.frauddetection.alert;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cathy.frauddetection.transaction.Decision;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

// MVC slice: under test is the HTTP contract — status codes, JSON shape,
// parameter binding — not the service logic behind it.
// GlobalExceptionHandler is package-private in another package, so it cannot be
// @Import-ed here; the advice scan reaches it by reflection anyway.
@WebMvcTest(AlertController.class)
class AlertControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AlertService alertService;

    private static Alert openAlert() {
        return new Alert(42L, 80, Decision.BLOCK,
                "AMOUNT_THRESHOLD,HIGH_RISK_COUNTRY");
    }

    @Test
    void listReturnsRuleCodesAsAnArray() throws Exception {
        when(alertService.findByStatus(eq(AlertStatus.OPEN), any()))
                .thenReturn(new PageImpl<>(List.of(openAlert())));

        mockMvc.perform(get("/api/v1/alerts").param("status", "OPEN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                // Array, not the stored comma string: the separator is a storage
                // detail the client must never have to know.
                .andExpect(jsonPath("$.content[0].triggeredRules").isArray())
                .andExpect(jsonPath("$.content[0].triggeredRules[0]")
                        .value("AMOUNT_THRESHOLD"))
                .andExpect(jsonPath("$.content[0].triggeredRules[1]")
                        .value("HIGH_RISK_COUNTRY"));
    }

    // Key present with a null value: the client must be able to tell
    // "not reviewed" from "no such field".
    @Test
    void unreviewedAlertKeepsANullReviewedAt() throws Exception {
        when(alertService.findByStatus(any(), any()))
                .thenReturn(new PageImpl<>(List.of(openAlert())));

        mockMvc.perform(get("/api/v1/alerts").param("status", "OPEN"))
                .andExpect(jsonPath("$.content[0].reviewedAt").value(nullValue()));
    }

    // status has no default, so omitting it is a client error, not an empty list.
    @Test
    void listWithoutStatusIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/alerts"))
                .andExpect(status().isBadRequest());

        verify(alertService, never()).findByStatus(any(), any());
    }

    // Enum binding failure is handled by Boot's defaults, not our
    // GlobalExceptionHandler: the status is right, the body shape is not ours.
    @Test
    void listWithUnknownStatusIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/alerts").param("status", "CLOSED"))
                .andExpect(status().isBadRequest());

        verify(alertService, never()).findByStatus(any(), any());
    }

    // The clamp happens before the service call, so the JSON body cannot show
    // it — capture the Pageable instead.
    @Test
    void oversizedPageIsTruncatedToTheMaximum() throws Exception {
        when(alertService.findByStatus(any(), any()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 100), 0));

        mockMvc.perform(get("/api/v1/alerts")
                        .param("status", "OPEN")
                        .param("size", "500"))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(alertService).findByStatus(eq(AlertStatus.OPEN), captor.capture());
        Assertions.assertEquals(100, captor.getValue().getPageSize());
    }

    // Sort is fixed server-side. Capturing proves the tie-breaker is appended;
    // a response body with distinct timestamps would prove nothing.
    @Test
    void listAlwaysSortsByCreatedAtThenId() throws Exception {
        when(alertService.findByStatus(any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/v1/alerts").param("status", "OPEN"))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(alertService).findByStatus(any(), captor.capture());
        Assertions.assertEquals(
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")),
                captor.getValue().getSort());
    }

    @Test
    void reviewReturnsTheUpdatedAlert() throws Exception {
        Alert reviewed = openAlert();
        reviewed.review(AlertStatus.CONFIRMED);
        when(alertService.review(7L, AlertStatus.CONFIRMED)).thenReturn(reviewed);

        mockMvc.perform(patch("/api/v1/alerts/7")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CONFIRMED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.reviewedAt").isNotEmpty());
    }

    // OPEN is a valid enum value but not a review outcome. Rejected at the
    // boundary, so the client gets 400 instead of the entity's 500.
    // Asserting the title matters: a plain 400 could also be Tomcat's default.
    @Test
    void reviewingIntoOpenIsRejectedWithProblemDetail() throws Exception {
        mockMvc.perform(patch("/api/v1/alerts/7")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"OPEN\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid review outcome"));

        verify(alertService, never()).review(any(), any());
    }

    // 404, not 400: the id is a well-formed long that points at nothing.
    @Test
    void reviewingAMissingAlertReturnsNotFound() throws Exception {
        when(alertService.review(eq(999999L), any()))
                .thenThrow(new AlertNotFoundException(999999L));

        mockMvc.perform(patch("/api/v1/alerts/999999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CONFIRMED\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Alert not found"));
    }

    // Bean Validation failures go through Boot's defaults, not
    // GlobalExceptionHandler — the same fact already pinned on the transaction side.
    @Test
    void reviewWithoutAStatusIsRejected() throws Exception {
        mockMvc.perform(patch("/api/v1/alerts/7")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        verify(alertService, never()).review(any(), any());
    }
}