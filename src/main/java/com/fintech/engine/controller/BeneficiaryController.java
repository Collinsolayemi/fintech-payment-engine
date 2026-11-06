package com.fintech.engine.controller;
import com.fintech.engine.dto.*;
import com.fintech.engine.service.BeneficiaryService;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.*;
@RestController
@RequestMapping("/api/v1/beneficiaries")
@Tag(name="Beneficiaries", description="Manage the authenticated customer's saved recipients")
public class BeneficiaryController {
    private final BeneficiaryService service;
    public BeneficiaryController(BeneficiaryService service) { this.service = service; }
    @GetMapping public List<BeneficiaryResponse> list(Authentication auth) { return service.list(auth.getName()); }
    @PostMapping public ResponseEntity<BeneficiaryResponse> create(Authentication auth, @Valid @RequestBody CreateBeneficiaryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(auth.getName(), request));
    }
    @PatchMapping("/{id}") public BeneficiaryResponse rename(Authentication auth, @PathVariable UUID id, @Valid @RequestBody RenameBeneficiaryRequest request) {
        return service.rename(auth.getName(), id, request);
    }
    @DeleteMapping("/{id}") public ResponseEntity<Void> delete(Authentication auth, @PathVariable UUID id) {
        service.delete(auth.getName(), id); return ResponseEntity.noContent().build();
    }
}
