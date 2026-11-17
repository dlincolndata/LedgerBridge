package com.dlincolndata.ledgerbridge.api.v1;
public class IdempotencyConflictException extends RuntimeException {
    public IdempotencyConflictException(String msg) { super(msg); }
}
