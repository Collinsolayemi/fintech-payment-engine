package com.fintech.engine.service;

import com.fintech.engine.exception.IdempotencyConflictException;
import com.fintech.engine.model.entity.IdempotencyRecord;
import com.fintech.engine.repository.IdempotencyRecordRepository;
import com.fintech.engine.service.impl.IdempotencyServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IdempotencyServiceTest {

    @Mock
    private IdempotencyRecordRepository idempotencyRecordRepository;

    private IdempotencyServiceImpl idempotencyService;

    @BeforeEach
    void setUp() {
        idempotencyService = new IdempotencyServiceImpl(idempotencyRecordRepository);
    }

    @Test
    @DisplayName("Should register new key as IN_FLIGHT and return empty optional")
    void shouldRegisterNewKeyAsInFlight() {
        when(idempotencyRecordRepository.findById("key-123")).thenReturn(Optional.empty());

        Optional<IdempotencyRecord> result = idempotencyService.checkOrStart("key-123", "hash-abc");

        assertTrue(result.isEmpty());
        verify(idempotencyRecordRepository).save(any(IdempotencyRecord.class));
    }

    @Test
    @DisplayName("Should return cached record when idempotency key is already COMPLETED")
    void shouldReturnCachedRecordWhenCompleted() {
        IdempotencyRecord completed = new IdempotencyRecord("key-123", "hash-abc", "COMPLETED", OffsetDateTime.now().plusHours(24));
        completed.markCompleted(200, "{\"status\":\"OK\"}");

        when(idempotencyRecordRepository.findById("key-123")).thenReturn(Optional.of(completed));

        Optional<IdempotencyRecord> result = idempotencyService.checkOrStart("key-123", "hash-abc");

        assertTrue(result.isPresent());
        assertEquals("COMPLETED", result.get().getStatus());
        assertEquals(200, result.get().getResponseCode());
        assertEquals("{\"status\":\"OK\"}", result.get().getResponseBody());
        verify(idempotencyRecordRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should throw IdempotencyConflictException when key is IN_FLIGHT")
    void shouldThrowConflictWhenKeyIsInFlight() {
        IdempotencyRecord inFlight = new IdempotencyRecord("key-123", "hash-abc", "IN_FLIGHT", OffsetDateTime.now().plusHours(24));
        when(idempotencyRecordRepository.findById("key-123")).thenReturn(Optional.of(inFlight));

        assertThrows(IdempotencyConflictException.class, () ->
                idempotencyService.checkOrStart("key-123", "hash-abc")
        );
    }

    @Test
    @DisplayName("Should mark key COMPLETED with response payload")
    void shouldMarkKeyCompleted() {
        IdempotencyRecord record = new IdempotencyRecord("key-123", "hash-abc", "IN_FLIGHT", OffsetDateTime.now().plusHours(24));
        when(idempotencyRecordRepository.findById("key-123")).thenReturn(Optional.of(record));

        idempotencyService.complete("key-123", 200, "{\"success\":true}");

        assertEquals("COMPLETED", record.getStatus());
        assertEquals(200, record.getResponseCode());
        assertEquals("{\"success\":true}", record.getResponseBody());
        verify(idempotencyRecordRepository).save(record);
    }
}

