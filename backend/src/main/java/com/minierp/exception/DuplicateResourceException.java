package com.minierp.exception;

public class DuplicateResourceException extends DomainException {

    public DuplicateResourceException(String message) {
        super("DUPLICATE_RESOURCE", message);
    }
}
