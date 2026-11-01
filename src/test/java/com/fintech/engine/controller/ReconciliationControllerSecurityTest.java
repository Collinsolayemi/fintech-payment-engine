package com.fintech.engine.controller;

import com.fintech.engine.exception.GlobalExceptionHandler;
import com.fintech.engine.model.enums.ReconciliationStatus;
import com.fintech.engine.model.enums.ReconciliationType;
import com.fintech.engine.service.ReconciliationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the @PreAuthorize("hasRole('ADMIN')") guard with method security enabled
 * (the HTTP filter chain is excluded so only method-level authorization is exercised).
 */
@WebMvcTest(controllers = ReconciliationController.class, excludeAutoConfiguration = {
        SecurityAutoConfiguration.class,
        SecurityFilterAutoConfiguration.class
})
@Import({GlobalExceptionHandler.class, ReconciliationControllerSecurityTest.MethodSecurityTestConfig.class})
class ReconciliationControllerSecurityTest {

    @TestConfiguration
    @EnableMethodSecurity
    static class MethodSecurityTestConfig {
    }

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ReconciliationService reconciliationService;

    @Test
    @WithMockUser(username = "customer@fintech.com", roles = "USER")
    @DisplayName("Non-admin users should receive 403 Forbidden")
    void nonAdminShouldBeForbidden() throws Exception {
        mockMvc.perform(post("/api/v1/reconciliation/runs"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        verify(reconciliationService, never()).runInternalReconciliation(anyString());
    }

    @Test
    @WithMockUser(username = "admin@fintech.com", roles = "ADMIN")
    @DisplayName("Admin users should be allowed to trigger a run")
    void adminShouldBeAllowed() throws Exception {
        when(reconciliationService.runInternalReconciliation(anyString())).thenReturn(
                ReconciliationControllerTest.sampleRun(ReconciliationType.INTERNAL_INTEGRITY, ReconciliationStatus.PASSED, List.of()));

        mockMvc.perform(post("/api/v1/reconciliation/runs"))
                .andExpect(status().isCreated());
    }
}

