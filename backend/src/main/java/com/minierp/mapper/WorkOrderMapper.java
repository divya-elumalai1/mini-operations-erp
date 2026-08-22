package com.minierp.mapper;

import com.minierp.domain.WorkOrder;
import com.minierp.dto.WorkOrderResponse;

public final class WorkOrderMapper {

    private WorkOrderMapper() {
    }

    public static WorkOrderResponse toResponse(WorkOrder wo) {
        return new WorkOrderResponse(
                wo.getId(),
                wo.getWoNumber(),
                wo.getItem().getId(),
                wo.getItem().getSku(),
                wo.getItem().getName(),
                wo.getLocation().getId(),
                wo.getLocation().getCode(),
                wo.getRequiredQuantity(),
                wo.getAssignedUser().getId(),
                wo.getAssignedUser().getUsername(),
                wo.getStatus().name(),
                wo.getCompletedAt(),
                wo.getCreatedBy().getUsername(),
                wo.getCreatedAt());
    }
}
