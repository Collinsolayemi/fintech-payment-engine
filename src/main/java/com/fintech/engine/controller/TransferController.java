package com.fintech.engine.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintech.engine.dto.TransferRequest;
import com.fintech.engine.dto.TransferResponse;
import com.fintech.engine.model.entity.IdempotencyRecord;
import com.fintech.engine.service.IdempotencyService;
import com.fintech.engine.service.TransferService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/transfers")
public class TransferController {

    private final TransferService transferService;
    private final IdempotencyService idempotencyService;
    private final ObjectMapper objectMapper;

    public TransferController(TransferService transferService,
                              IdempotencyService idempotencyService,
                              ObjectMapper objectMapper) {
        this.transferService = transferService;
        this.idempotencyService = idempotencyService;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    public ResponseEntity<TransferResponse> initiateTransfer(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody TransferRequest request) {

        String requestHash = computeRequestHash("/api/v1/transfers", request);

        // 1. Check idempotency if key was provided
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            Optional<IdempotencyRecord> cached = idempotencyService.checkOrStart(idempotencyKey, requestHash);
            if (cached.isPresent()) {
                try {
                    TransferResponse cachedResponse = objectMapper.readValue(
                            cached.get().getResponseBody(), TransferResponse.class);
                    return ResponseEntity.status(cached.get().getResponseCode()).body(cachedResponse);
                } catch (JsonProcessingException e) {
                    // Fallback to re-executing if cache deserialization fails
                }
            }
        }

        try {
            // 2. Execute transfer
            TransferResponse response = transferService.transfer(request, idempotencyKey);

            // 3. Cache response in idempotency store
            if (idempotencyKey != null && !idempotencyKey.isBlank()) {
                try {
                    String jsonResponse = objectMapper.writeValueAsString(response);
                    idempotencyService.complete(idempotencyKey, HttpStatus.OK.value(), jsonResponse);
                } catch (JsonProcessingException ignored) {}
            }

            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            if (idempotencyKey != null && !idempotencyKey.isBlank()) {
                idempotencyService.fail(idempotencyKey);
            }
            throw e;
        }
    }

    @GetMapping("/{reference}")
    public ResponseEntity<TransferResponse> getTransfer(@PathVariable String reference) {
        return ResponseEntity.ok(transferService.getTransferByReference(reference));
    }

    private String computeRequestHash(String endpoint, Object requestBody) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String raw = endpoint + ":" + (requestBody != null ? requestBody.toString() : "");
            byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            return "hash-unavailable";
        }
    }
}

