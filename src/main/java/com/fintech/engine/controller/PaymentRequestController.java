package com.fintech.engine.controller;

import com.fintech.engine.dto.CreatePaymentRequestDto;
import com.fintech.engine.dto.PayPaymentRequestDto;
import com.fintech.engine.dto.PaymentRequestResponse;
import com.fintech.engine.service.PaymentRequestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payment-requests")
@Tag(name = "Payment Requests", description = "Create, share, pay, decline, and cancel P2P payment requests")
public class PaymentRequestController {

    private final PaymentRequestService paymentRequestService;

    public PaymentRequestController(PaymentRequestService paymentRequestService) {
        this.paymentRequestService = paymentRequestService;
    }

    @PostMapping
    @Operation(summary = "Create a payment request or shareable payment link")
    public ResponseEntity<PaymentRequestResponse> create(
            Authentication authentication,
            @Valid @RequestBody CreatePaymentRequestDto request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(paymentRequestService.create(authentication.getName(), request));
    }

    @GetMapping("/sent")
    @Operation(summary = "List payment requests created by the authenticated user")
    public List<PaymentRequestResponse> listSent(Authentication authentication) {
        return paymentRequestService.listSent(authentication.getName());
    }

    @GetMapping("/incoming")
    @Operation(summary = "List payment requests directed to the authenticated user")
    public List<PaymentRequestResponse> listIncoming(Authentication authentication) {
        return paymentRequestService.listIncoming(authentication.getName());
    }

    @GetMapping("/code/{requestCode}")
    @Operation(summary = "Look up a payment request by its shareable request code")
    public PaymentRequestResponse getByCode(@PathVariable String requestCode) {
        return paymentRequestService.getByCode(requestCode);
    }

    @PostMapping("/{id}/pay")
    @Operation(summary = "Fulfill a pending payment request from one of your wallets")
    public PaymentRequestResponse pay(
            Authentication authentication,
            @PathVariable UUID id,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody PayPaymentRequestDto request
    ) {
        return paymentRequestService.pay(authentication.getName(), id, request, idempotencyKey);
    }

    @PostMapping("/{id}/decline")
    @Operation(summary = "Decline an incoming payment request")
    public PaymentRequestResponse decline(Authentication authentication, @PathVariable UUID id) {
        return paymentRequestService.decline(authentication.getName(), id);
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancel a sent payment request")
    public PaymentRequestResponse cancel(Authentication authentication, @PathVariable UUID id) {
        return paymentRequestService.cancel(authentication.getName(), id);
    }
}

