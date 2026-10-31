package com.fintech.engine.exception;

import java.util.UUID;

public class SelfTransferException extends FintechException {
    public SelfTransferException(UUID accountId) {
        super("Sender and recipient account cannot be the same: " + accountId, "SELF_TRANSFER_NOT_ALLOWED");
    }
}

