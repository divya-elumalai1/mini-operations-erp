package com.minierp.controller;

import com.minierp.dto.PageResponse;
import com.minierp.dto.TransferCreateRequest;
import com.minierp.dto.TransferResponse;
import com.minierp.domain.TransferStatus;
import com.minierp.service.TransferService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@Tag(name = "Internal Transfers", description = "Stock movements between locations: REQUESTED → DISPATCHED → RECEIVED")
@RestController
@RequestMapping("/api/transfers")
@SecurityRequirement(name = "bearerAuth")
public class TransferController {

    private final TransferService transferService;

    public TransferController(TransferService transferService) {
        this.transferService = transferService;
    }

    @Operation(summary = "Create a transfer request (REQUESTED). ADMIN or OPERATIONS_USER.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATIONS_USER')")
    public TransferResponse create(@Valid @RequestBody TransferCreateRequest request,
                                   org.springframework.security.core.Authentication authentication) {
        return transferService.create(request, authentication.getName());
    }

    @Operation(summary = "List transfers (filter by status, paginated). Any authenticated role.")
    @GetMapping
    public PageResponse<TransferResponse> list(@RequestParam Optional<TransferStatus> status,
                                               @RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "20") int size) {
        return transferService.list(status, PageRequest.of(Math.max(page, 0),
                Math.min(Math.max(size, 1), 100)));
    }

    @Operation(summary = "Get one transfer. Any authenticated role.")
    @GetMapping("/{id}")
    public TransferResponse get(@PathVariable Long id) {
        return transferService.get(id);
    }

    @Operation(summary = "Dispatch: source stock decreases, destination unchanged. ADMIN or OPERATIONS_USER.")
    @PostMapping("/{id}/dispatch")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATIONS_USER')")
    public TransferResponse dispatch(@PathVariable Long id,
                                     org.springframework.security.core.Authentication authentication) {
        return transferService.dispatch(id, authentication.getName());
    }

    @Operation(summary = "Receive: destination stock increases. Single-shot; duplicates are rejected.")
    @PostMapping("/{id}/receive")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATIONS_USER')")
    public TransferResponse receive(@PathVariable Long id,
                                    org.springframework.security.core.Authentication authentication) {
        return transferService.receive(id, authentication.getName());
    }
}
