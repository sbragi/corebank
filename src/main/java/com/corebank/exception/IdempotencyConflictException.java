package com.corebank.exception;
public class IdempotencyConflictException extends RuntimeException {
    public IdempotencyConflictException() { super("Invalid or conflicting idempotency key"); }
}
