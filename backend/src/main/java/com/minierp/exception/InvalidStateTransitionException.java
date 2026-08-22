package com.minierp.exception;

/** A lifecycle transition that the business rules do not allow (e.g. receiving twice). */
public class InvalidStateTransitionException extends DomainException {

    public InvalidStateTransitionException(String message) {
        super("INVALID_STATE_TRANSITION", message);
    }
}
