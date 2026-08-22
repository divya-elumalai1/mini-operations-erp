package com.minierp.exception;

public class BusinessRuleViolationException extends DomainException {

    public BusinessRuleViolationException(String message) {
        super("BUSINESS_RULE_VIOLATION", message);
    }
}
