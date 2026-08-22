package com.minierp.mapper;

import com.minierp.domain.CustomerOrder;
import com.minierp.domain.InternalTransfer;
import com.minierp.dto.OrderResponse;
import com.minierp.dto.TransferResponse;

public final class OrderTransferMapper {

    private OrderTransferMapper() {
    }

    public static TransferResponse toResponse(InternalTransfer t) {
        return new TransferResponse(
                t.getId(),
                t.getTransferNumber(),
                t.getItem().getId(),
                t.getItem().getSku(),
                t.getQuantity(),
                t.getSourceLocation().getId(),
                t.getSourceLocation().getCode(),
                t.getDestinationLocation().getId(),
                t.getDestinationLocation().getCode(),
                t.getStatus().name(),
                t.getDispatchedBy() == null ? null : t.getDispatchedBy().getUsername(),
                t.getDispatchedAt(),
                t.getReceivedBy() == null ? null : t.getReceivedBy().getUsername(),
                t.getReceivedAt(),
                t.getCreatedBy().getUsername(),
                t.getCreatedAt());
    }

    public static OrderResponse toResponse(CustomerOrder o) {
        return new OrderResponse(
                o.getId(),
                o.getOrderNumber(),
                o.getCustomerName(),
                o.getStatus().name(),
                o.getItems().stream()
                        .map(line -> new OrderResponse.OrderLineResponse(
                                line.getId(),
                                line.getItem().getId(),
                                line.getItem().getSku(),
                                line.getLocation().getId(),
                                line.getLocation().getCode(),
                                line.getQuantity()))
                        .toList(),
                o.getCreatedBy().getUsername(),
                o.getCreatedAt());
    }
}
