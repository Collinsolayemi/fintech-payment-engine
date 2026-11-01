package com.fintech.engine.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintech.engine.dto.ReconciliationDiscrepancyResponse;
import com.fintech.engine.dto.ReconciliationRunResponse;
import com.fintech.engine.dto.SettlementReconciliationRequest;
import com.fintech.engine.dto.SettlementRecord;
import com.fintech.engine.exception.GlobalExceptionHandler;
import com.fintech.engine.exception.ReconciliationRunNotFoundException;
import com.fintech.engine.model.enums.DiscrepancySeverity;
import com.fintech.engine.model.enums.DiscrepancyType;
import com.fintech.engine.model.enums.ReconciliationStatus;
import com.fintech.engine.model.enums.ReconciliationType;
import com.fintech.engine.model.enums.WebhookProvider;
import com.fintech.engine.service.ReconciliationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = ReconciliationController.class, excludeAutoConfiguration = {
        SecurityAutoConfiguration.class,
        SecurityFilterAutoConfiguration.class
})
@Import(GlobalExceptionHandler.class)
class ReconciliationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ReconciliationService reconciliationService;

    static ReconciliationRunResponse sampleRun(ReconciliationType type, ReconciliationStatus status,
                                               List<ReconciliationDiscrepancyResponse> discrepancies) {
        OffsetDateTime now = OffsetDateTime.now();
        return new ReconciliationRunResponse(
                UUID.randomUUID(), type, status,
                type == ReconciliationType.GATEWAY_SETTLEMENT ? WebhookProvider.STRIPE : null,
                "admin@fintech.com", null, null,
                new BigDecimal("100.00"), new BigDecimal("100.00"), null, null, null,
                discrepancies == null ? 0 : discrepancies.size(), null, now, now, discrepancies);
    }

    @Test
    @DisplayName("POST /api/v1/reconciliation/runs - Should execute internal run and return 201 Created")
    void shouldRunInternalReconciliation() throws Exception {
        when(reconciliationService.runInternalReconciliation(anyString()))
                .thenReturn(sampleRun(ReconciliationType.INTERNAL_INTEGRITY, ReconciliationStatus.PASSED, List.of()));

        mockMvc.perform(post("/api/v1/reconciliation/runs"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("INTERNAL_INTEGRITY"))
                .andExpect(jsonPath("$.status").value("PASSED"))
                .andExpect(jsonPath("$.discrepancyCount").value(0))
                .andExpect(jsonPath("$.provider").doesNotExist());
    }

    @Test
    @DisplayName("POST /api/v1/reconciliation/settlements/{provider} - Should reconcile settlement report and return discrepancies")
    void shouldRunSettlementReconciliation() throws Exception {
        ReconciliationDiscrepancyResponse ghost = new ReconciliationDiscrepancyResponse(
                UUID.randomUUID(), DiscrepancyType.GHOST_IN_LEDGER, DiscrepancySeverity.CRITICAL,
                "evt_ghost", null, "USD", null, new BigDecimal("99.00"),
                "Ledger posting DEP-STRIPE-evt_ghost credited 99.00 but STRIPE did not settle it", OffsetDateTime.now());

        when(reconciliationService.runSettlementReconciliation(eq(WebhookProvider.STRIPE), any(SettlementReconciliationRequest.class), anyString()))
                .thenReturn(sampleRun(ReconciliationType.GATEWAY_SETTLEMENT, ReconciliationStatus.FAILED, List.of(ghost)));

        SettlementReconciliationRequest request = new SettlementReconciliationRequest(
                OffsetDateTime.parse("2026-10-31T00:00:00Z"),
                OffsetDateTime.parse("2026-11-01T00:00:00Z"),
                List.of(new SettlementRecord("evt_1", new BigDecimal("100.00"), "USD")));

        mockMvc.perform(post("/api/v1/reconciliation/settlements/STRIPE")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.provider").value("STRIPE"))
                .andExpect(jsonPath("$.discrepancies.length()").value(1))
                .andExpect(jsonPath("$.discrepancies[0].type").value("GHOST_IN_LEDGER"))
                .andExpect(jsonPath("$.discrepancies[0].severity").value("CRITICAL"));
    }

    @Test
    @DisplayName("POST /api/v1/reconciliation/settlements/{provider} - Should return 400 when required fields are missing")
    void shouldRejectInvalidSettlementPayload() throws Exception {
        Map<String, Object> invalid = Map.of(
                "periodEnd", "2026-11-01T00:00:00Z",
                "records", List.of(Map.of("reference", "", "amount", -5, "currency", "US"))
        );

        mockMvc.perform(post("/api/v1/reconciliation/settlements/STRIPE")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.periodStart").exists());

        verify(reconciliationService, never()).runSettlementReconciliation(any(), any(), anyString());
    }

    @Test
    @DisplayName("GET /api/v1/reconciliation/runs - Should list run summaries without discrepancies")
    void shouldListRuns() throws Exception {
        when(reconciliationService.listRuns(0, 20)).thenReturn(List.of(
                sampleRun(ReconciliationType.INTERNAL_INTEGRITY, ReconciliationStatus.PASSED, null),
                sampleRun(ReconciliationType.GATEWAY_SETTLEMENT, ReconciliationStatus.FAILED, null)
        ));

        mockMvc.perform(get("/api/v1/reconciliation/runs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].discrepancies").doesNotExist())
                .andExpect(jsonPath("$[1].status").value("FAILED"));
    }

    @Test
    @DisplayName("GET /api/v1/reconciliation/runs/{id} - Should return 404 for unknown run")
    void shouldReturn404ForUnknownRun() throws Exception {
        UUID runId = UUID.randomUUID();
        when(reconciliationService.getRun(runId)).thenThrow(new ReconciliationRunNotFoundException(runId));

        mockMvc.perform(get("/api/v1/reconciliation/runs/" + runId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RECONCILIATION_RUN_NOT_FOUND"));
    }
}
