package com.minierp.controller;

import com.minierp.domain.WorkOrderStatus;
import com.minierp.dto.*;
import com.minierp.service.WorkOrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@Tag(name = "Work Orders", description = "Production/material work orders with backend shortage calculation")
@RestController
@RequestMapping("/api/work-orders")
@SecurityRequirement(name = "bearerAuth")
public class WorkOrderController {

    private final WorkOrderService workOrderService;

    public WorkOrderController(WorkOrderService workOrderService) {
        this.workOrderService = workOrderService;
    }

    @Operation(summary = "Create a work order. Requires ADMIN.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public WorkOrderResponse create(@Valid @RequestBody WorkOrderCreateRequest request,
                                    org.springframework.security.core.Authentication authentication) {
        return workOrderService.create(request, authentication.getName());
    }

    @Operation(summary = "List work orders (filter by status/location, paginated). Any authenticated role.")
    @GetMapping
    public PageResponse<WorkOrderResponse> list(@RequestParam Optional<WorkOrderStatus> status,
                                                @RequestParam Optional<Long> locationId,
                                                @RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "20") int size) {
        return workOrderService.list(status, locationId, PageRequest.of(Math.max(page, 0),
                Math.min(Math.max(size, 1), 100)));
    }

    @Operation(summary = "Get one work order. Any authenticated role.")
    @GetMapping("/{id}")
    public WorkOrderResponse get(@PathVariable Long id) {
        return workOrderService.get(id);
    }

    @Operation(summary = "Transition status (ASSIGNED -> IN_PROGRESS -> COMPLETED only). ADMIN or OPERATIONS_USER.")
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATIONS_USER')")
    public WorkOrderResponse changeStatus(@PathVariable Long id,
                                          @Valid @RequestBody WorkOrderStatusUpdateRequest request) {
        return workOrderService.changeStatus(id, request.status());
    }

    @Operation(summary = "Material availability & shortage, computed on the server. Any authenticated role.")
    @GetMapping("/{id}/material-availability")
    public MaterialAvailabilityResponse materialAvailability(@PathVariable Long id) {
        return workOrderService.getMaterialAvailability(id);
    }
}
