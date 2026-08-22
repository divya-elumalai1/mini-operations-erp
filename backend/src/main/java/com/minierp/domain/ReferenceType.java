package com.minierp.domain;

/** What business object caused an inventory movement. */
public enum ReferenceType {
    MANUAL,
    INTERNAL_TRANSFER,
    CUSTOMER_ORDER,
    WORK_ORDER
}
