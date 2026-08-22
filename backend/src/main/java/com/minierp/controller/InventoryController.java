package com.minierp.controller;

import com.minierp.dto.*;
import com.minierp.service.InventoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@Tag(name = "Inventory", description = "Stock positions, goods receipts and the audit ledger")
@RestController
@RequestMapping("/api/inventory")
@SecurityRequirement(name = "bearerAuth")
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @Operation(summary = "List stock positions (filter by item/location, paginated). Any authenticated role.")
    @GetMapping
    public PageResponse<InventoryResponse> list(@RequestParam Optional<Long> itemId,
                                                @RequestParam Optional<Long> locationId,
                                                @RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "20") int size) {
        return inventoryService.searchInventory(itemId, locationId, pageable(page, size));
    }

    @Operation(summary = "List the inventory transaction audit ledger (filterable, paginated).")
    @GetMapping("/transactions")
    public PageResponse<InventoryTransactionResponse> transactions(@RequestParam Optional<Long> itemId,
                                                                   @RequestParam Optional<Long> locationId,
                                                                   @RequestParam Optional<String> type,
                                                                   @RequestParam(defaultValue = "0") int page,
                                                                   @RequestParam(defaultValue = "20") int size) {
        return inventoryService.searchTransactions(itemId, locationId, type, pageableDesc(page, size));
    }

    @Operation(summary = "Goods receipt: add physical stock. Requires ADMIN or OPERATIONS_USER.")
    @PostMapping("/receipts")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATIONS_USER')")
    public InventoryResponse receipt(@Valid @RequestBody ReceiptRequest request,
                                     Authentication authentication) {
        return inventoryService.receipt(request, authentication.getName());
    }

    private static Pageable pageable(int page, int size) {
        return PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.ASC, "id"));
    }

    private static Pageable pageableDesc(int page, int size) {
        return PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "createdAt"));
    }
}
