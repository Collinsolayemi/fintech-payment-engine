package com.fintech.engine.service;

import com.fintech.engine.model.entity.IdempotencyRecord;

import java.util.Optional;

public interface IdempotencyService {

    /**
     * Checks if a request with this idempotency key was already completed, in-flight, or new.
     * Returns cached IdempotencyRecord if already completed, throws IdempotencyConflictException if in-flight.
     * Returns empty Optional if this is a new valid request and registers it as IN_FLIGHT.
     */
    Optional<IdempotencyRecord> checkOrStart(String key, String requestHash);

    /**
     * Marks the idempotency key as COMPLETED with the given HTTP status code and response JSON body.
     */
    void complete(String key, int statusCode, String responseBody);

    /**
     * Removes or marks the idempotency key as FAILED upon unhandled failure so the client can retry.
     */
    void fail(String key);
}

