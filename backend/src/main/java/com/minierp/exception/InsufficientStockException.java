package com.minierp.exception;

/** Requested stock movement exceeds what is physically possible at this moment. */
public class InsufficientStockException extends DomainException {

    public InsufficientStockException(String message) {
        super("INSUFFICIENT_STOCK", message);
    }
}
