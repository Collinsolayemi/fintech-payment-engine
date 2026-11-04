package com.fintech.engine.dto;

import com.fintech.engine.model.enums.CardStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateCardStatusRequest(
        @NotNull
        CardStatus status
) {}

