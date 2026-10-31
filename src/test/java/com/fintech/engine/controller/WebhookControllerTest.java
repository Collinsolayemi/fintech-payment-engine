package com.fintech.engine.controller;

import com.fintech.engine.dto.WebhookResponse;
import com.fintech.engine.exception.GlobalExceptionHandler;
import com.fintech.engine.exception.InvalidWebhookSignatureException;
import com.fintech.engine.model.enums.WebhookProvider;
import com.fintech.engine.model.enums.WebhookStatus;
import com.fintech.engine.service.WebhookService;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = WebhookController.class, excludeAutoConfiguration = {
        SecurityAutoConfiguration.class,
        SecurityFilterAutoConfiguration.class
})
@Import(GlobalExceptionHandler.class)
class WebhookControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private WebhookService webhookService;

    @Test
    @DisplayName("POST /api/v1/webhooks/STRIPE - Should ingest valid webhook and return 200 OK")
    void shouldIngestValidWebhook() throws Exception {
        String payload = "{\"id\":\"evt_001\",\"type\":\"payment.succeeded\"}";
        WebhookResponse response = new WebhookResponse(true, "Processed", "evt_001", WebhookStatus.PROCESSED);

        when(webhookService.processWebhook(eq(WebhookProvider.STRIPE), eq("sig_123"), any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/webhooks/STRIPE")
                        .header("X-Signature", "sig_123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.eventId").value("evt_001"))
                .andExpect(jsonPath("$.status").value("PROCESSED"));
    }

    @Test
    @DisplayName("POST /api/v1/webhooks/STRIPE - Should return 401 Unauthorized on invalid signature")
    void shouldReturn401OnInvalidSignature() throws Exception {
        String payload = "{\"id\":\"evt_001\"}";
        when(webhookService.processWebhook(eq(WebhookProvider.STRIPE), any(), any()))
                .thenThrow(new InvalidWebhookSignatureException());

        mockMvc.perform(post("/api/v1/webhooks/STRIPE")
                        .header("X-Signature", "bad_signature")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_WEBHOOK_SIGNATURE"));
    }
}

