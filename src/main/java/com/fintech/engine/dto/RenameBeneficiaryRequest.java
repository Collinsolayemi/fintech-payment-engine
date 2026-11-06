package com.fintech.engine.dto;
import jakarta.validation.constraints.*;
public record RenameBeneficiaryRequest(@NotBlank @Size(max=100) String nickname) {}
