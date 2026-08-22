package com.minierp.service;

import com.minierp.domain.*;
import com.minierp.dto.OrderCreateRequest;
import com.minierp.dto.OrderResponse;
import com.minierp.dto.PageResponse;
import com.minierp.exception.BusinessRuleViolationException;
import com.minierp.exception.ResourceNotFoundException;
import com.minierp.mapper.OrderTransferMapper;
import com.minierp.repository.CustomerOrderRepository;
import com.minierp.repository.ItemRepository;
import com.minierp.repository.LocationRepository;
import com.minierp.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final CustomerOrderRepository orderRepository;
    private final ItemRepository itemRepository;
    private final LocationRepository locationRepository;
    private final UserRepository userRepository;
    private final InventoryLedger ledger;

    public OrderService(CustomerOrderRepository orderRepository,
                        ItemRepository itemRepository,
                        LocationRepository locationRepository,
                        UserRepository userRepository,
                        InventoryLedger ledger) {
        this.orderRepository = orderRepository;
        this.itemRepository = itemRepository;
        this.locationRepository = locationRepository;
        this.userRepository = userRepository;
        this.ledger = ledger;
    }

    /**
     * Creates the customer order and reserves stock for every line in ONE transaction.
     * If any line cannot be reserved, the exception rolls back the order header, all
     * earlier lines' reservations and every ledger row — no partial state is possible.
     * Lines are merged per (item, location) and processed in deterministic
     * (itemId, locationId) order so concurrent orders always lock inventory rows in a
     * consistent sequence (deadlock prevention).
     */
    @Transactional
    public OrderResponse createOrderWithReservation(OrderCreateRequest request, String username) {
        User creator = requireUser(username);
        Map<String, BigDecimal> merged = mergeAndSortLines(request);

        for (Map.Entry<String, BigDecimal> entry : merged.entrySet()) {
            String[] parts = entry.getKey().split(":");
            long itemId = Long.parseLong(parts[0]);
            long locationId = Long.parseLong(parts[1]);
            requireActiveItem(itemId);
            requireActiveLocation(locationId);
        }

        String orderNumber = "SO-%06d".formatted(orderRepository.nextOrderNumber());
        CustomerOrder order = new CustomerOrder(orderNumber, request.customerName(), creator);
        for (Map.Entry<String, BigDecimal> entry : merged.entrySet()) {
            String[] parts = entry.getKey().split(":");
            order.addItem(new CustomerOrderItem(order,
                    itemRepository.getReferenceById(Long.parseLong(parts[0])),
                    locationRepository.getReferenceById(Long.parseLong(parts[1])),
                    entry.getValue()));
        }
        CustomerOrder saved = orderRepository.saveAndFlush(order);

        for (CustomerOrderItem line : saved.getItems()) {
            ledger.reserve(line.getItem().getId(), line.getLocation().getId(), line.getQuantity(),
                    ReferenceType.CUSTOMER_ORDER, saved.getId(), creator.getId());
        }
        log.info("Customer order created with reservation: {} lines={} by={}",
                orderNumber, saved.getItems().size(), username);
        return OrderTransferMapper.toResponse(loadWithDetails(saved.getId()));
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> list(Optional<CustomerOrderStatus> status, Pageable pageable) {
        Page<OrderResponse> page = orderRepository.search(status.orElse(null), pageable)
                .map(OrderTransferMapper::toResponse);
        return PageResponse.from(page);
    }

    @Transactional(readOnly = true)
    public OrderResponse get(Long id) {
        return OrderTransferMapper.toResponse(loadWithDetails(id));
    }

    /** Key format: "<itemId>:<locationId>", values summed; result sorted by key components. */
    private Map<String, BigDecimal> mergeAndSortLines(OrderCreateRequest request) {
        Map<String, BigDecimal> merged = new LinkedHashMap<>();
        for (OrderCreateRequest.OrderLineRequest line : request.lines()) {
            String key = "%d:%d".formatted(line.itemId(), line.locationId());
            merged.merge(key, line.quantity(), BigDecimal::add);
        }
        LinkedHashMap<String, BigDecimal> sorted = new LinkedHashMap<>();
        merged.entrySet().stream()
                .sorted(Comparator.comparing(e -> {
                    String[] p = e.getKey().split(":");
                    return Long.parseLong(p[0]) * 1_000_000L + Long.parseLong(p[1]);
                }))
                .forEach(e -> sorted.put(e.getKey(), e.getValue()));
        return sorted;
    }

    private CustomerOrder loadWithDetails(Long id) {
        return orderRepository.findWithDetailsById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Customer order", id));
    }

    private void requireActiveItem(Long itemId) {
        var item = itemRepository.findById(itemId)
                .orElseThrow(() -> ResourceNotFoundException.of("Item", itemId));
        if (!item.isActive()) {
            throw new BusinessRuleViolationException("Item is inactive: " + item.getSku());
        }
    }

    private void requireActiveLocation(Long locationId) {
        var location = locationRepository.findById(locationId)
                .orElseThrow(() -> ResourceNotFoundException.of("Location", locationId));
        if (!location.isActive()) {
            throw new BusinessRuleViolationException("Location is inactive: " + location.getCode());
        }
    }

    private User requireUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> ResourceNotFoundException.of("User", username));
    }
}
