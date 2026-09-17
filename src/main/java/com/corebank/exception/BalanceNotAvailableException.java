package com.corebank.exception;

import java.util.UUID;

public class BalanceNotAvailableException extends RuntimeException {
    public BalanceNotAvailableException(UUID accountId) {
        super("Balance is not available in cache for account " + accountId);
    }
}
