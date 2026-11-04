package com.fintech.engine.exception;

import java.util.UUID;

public class VirtualCardNotFoundException extends FintechException {
    public VirtualCardNotFoundException(UUID id) {
        super("Virtual card not found with ID: " + id, "VIRTUAL_CARD_NOT_FOUND");
    }
}

