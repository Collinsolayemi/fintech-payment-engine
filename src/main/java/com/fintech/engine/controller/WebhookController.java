package com.fintech.engine.controller;

import com.fintech.engine.dto.WebhookResponse;
import com.fintech.engine.model.enums.WebhookProvider;
import com.fintech.engine.service.WebhookService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Payment Webhooks", description = "Endpoints for cryptographically verified payment gateway webhook ingestion and wallet auto-funding")
@RestController
@RequestMapping("/api/v1/webhooks")
public class WebhookController {

    private final WebhookService webhookService;

    public WebhookController(WebhookService webhookService) {
        this.webhookService = webhookService;
    }

    @Operation(summary = "Ingest Payment Gateway Webhook", description = "Verifies HMAC-SHA256 signature, prevents replay attacks via event de-duplication, and auto-credits the recipient wallet.")
    @ApiResponse(responseCode = "200", description = "Webhook successfully verified and processed")
    @ApiResponse(responseCode = "401", description = "Invalid or tampered HMAC signature")
    @ApiResponse(responseCode = "400", description = "Malformed JSON payload")
    @PostMapping("/{provider}")
    public ResponseEntity<WebhookResponse> handleWebhook(
            @Parameter(description = "Provider name: STRIPE, PAYSTACK, FLUTTERWAVE") @PathVariable WebhookProvider provider,
            @RequestHeader(value = "X-Signature", required = false) String genericSignature,
            @RequestHeader(value = "Stripe-Signature", required = false) String stripeSignature,
            @RequestBody String rawPayload
    ) {
        String signature = stripeSignature != null ? stripeSignature : genericSignature;
        WebhookResponse response = webhookService.processWebhook(provider, signature, rawPayload);
        return ResponseEntity.ok(response);
    }
}

