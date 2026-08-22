package com.minierp.domain;

public enum WorkOrderStatus {
    ASSIGNED,
    IN_PROGRESS,
    COMPLETED;

    /** Forward-only lifecycle: ASSIGNED -> IN_PROGRESS -> COMPLETED. */
    public boolean canTransitionTo(WorkOrderStatus target) {
        return switch (this) {
            case ASSIGNED -> target == IN_PROGRESS;
            case IN_PROGRESS -> target == COMPLETED;
            case COMPLETED -> false;
        };
    }
}
