package com.minierp.mapper;

import com.minierp.domain.Inventory;
import com.minierp.domain.InventoryTransaction;
import com.minierp.dto.InventoryResponse;
import com.minierp.dto.InventoryTransactionResponse;

public final class InventoryMapper {

    private InventoryMapper() {
    }

    public static InventoryResponse toResponse(Inventory inventory) {
        return new InventoryResponse(
                inventory.getId(),
                inventory.getItem().getId(),
                inventory.getItem().getSku(),
                inventory.getItem().getName(),
                inventory.getItem().getUnitOfMeasure(),
                inventory.getLocation().getId(),
                inventory.getLocation().getCode(),
                inventory.getPhysicalQty(),
                inventory.getReservedQty(),
                inventory.getAvailableQty(),
                inventory.getUpdatedAt());
    }

    public static InventoryTransactionResponse toResponse(InventoryTransaction tx) {
        return new InventoryTransactionResponse(
                tx.getId(),
                tx.getItem().getId(),
                tx.getItem().getSku(),
                tx.getLocation().getId(),
                tx.getLocation().getCode(),
                tx.getTxType().name(),
                tx.getReferenceType().name(),
                tx.getReferenceId(),
                tx.getQuantityDeltaPhysical(),
                tx.getQuantityDeltaReserved(),
                tx.getNotes(),
                tx.getPerformedBy().getUsername(),
                tx.getCreatedAt());
    }
}
