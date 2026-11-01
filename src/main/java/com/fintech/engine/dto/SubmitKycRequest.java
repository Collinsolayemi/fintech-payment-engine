package com.fintech.engine.dto;

import com.fintech.engine.model.enums.IdType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SubmitKycRequest(
    @Min(value = 2, message = "Target tier must be either 2 or 3")
    @Max(value = 3, message = "Target tier must be either 2 or 3")
    int targetTier,

    @NotNull(message = "ID type is required")
    IdType idType,

    @NotBlank(message = "ID number is required")
    @Size(max = 128, message = "ID number cannot exceed 128 characters")
    String idNumber,

    @Size(max = 512, message = "Document URL cannot exceed 512 characters")
    String documentUrl
) {}

