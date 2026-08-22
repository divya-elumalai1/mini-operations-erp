package com.minierp.controller;

import com.minierp.domain.CustomerOrderStatus;
import com.minierp.dto.OrderCreateRequest;
import com.minierp.dto.OrderResponse;
import com.minierp.dto.PageResponse;
import com.minierp.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@Tag(name = "Customer Orders", description = "Sales orders with atomic stock reservation")
@RestController
@RequestMapping("/api/orders")
@SecurityRequirement(name = "bearerAuth")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @Operation(summary = "Create order and reserve stock atomically (fails entirely if any line lacks stock). SALES_USER or ADMIN.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('SALES_USER', 'ADMIN')")
    public OrderResponse create(@Valid @RequestBody OrderCreateRequest request,
                                org.springframework.security.core.Authentication authentication) {
        return orderService.createOrderWithReservation(request, authentication.getName());
    }

    @Operation(summary = "List orders (filter by status, paginated). Any authenticated role.")
    @GetMapping
    public PageResponse<OrderResponse> list(@RequestParam Optional<CustomerOrderStatus> status,
                                            @RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "20") int size) {
        return orderService.list(status, PageRequest.of(Math.max(page, 0),
                Math.min(Math.max(size, 1), 100)));
    }

    @Operation(summary = "Get one order with lines. Any authenticated role.")
    @GetMapping("/{id}")
    public OrderResponse get(@PathVariable Long id) {
        return orderService.get(id);
    }
}
