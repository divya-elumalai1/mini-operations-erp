package com.minierp.service;

import com.minierp.domain.*;
import com.minierp.dto.*;
import com.minierp.exception.BusinessRuleViolationException;
import com.minierp.exception.InvalidStateTransitionException;
import com.minierp.exception.ResourceNotFoundException;
import com.minierp.mapper.WorkOrderMapper;
import com.minierp.repository.InventoryRepository;
import com.minierp.repository.ItemRepository;
import com.minierp.repository.LocationRepository;
import com.minierp.repository.UserRepository;
import com.minierp.repository.WorkOrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;
import java.time.Instant;

@Service
public class WorkOrderService {

    private static final Logger log = LoggerFactory.getLogger(WorkOrderService.class);

    private final WorkOrderRepository workOrderRepository;
    private final ItemRepository itemRepository;
    private final LocationRepository locationRepository;
    private final UserRepository userRepository;
    private final InventoryRepository inventoryRepository;

    public WorkOrderService(WorkOrderRepository workOrderRepository,
                            ItemRepository itemRepository,
                            LocationRepository locationRepository,
                            UserRepository userRepository,
                            InventoryRepository inventoryRepository) {
        this.workOrderRepository = workOrderRepository;
        this.itemRepository = itemRepository;
        this.locationRepository = locationRepository;
        this.userRepository = userRepository;
        this.inventoryRepository = inventoryRepository;
    }

    @Transactional
    public WorkOrderResponse create(WorkOrderCreateRequest request, String createdByUsername) {
        User creator = requireUser(createdByUsername);
        User assignee = userRepository.findById(request.assignedUserId())
                .orElseThrow(() -> ResourceNotFoundException.of("User", request.assignedUserId()));
        if (assignee.getRole() != Role.OPERATIONS_USER) {
            throw new BusinessRuleViolationException(
                    "Work orders can only be assigned to OPERATIONS_USER accounts");
        }
        if (!assignee.isActive()) {
            throw new BusinessRuleViolationException("Assigned user is inactive");
        }
        Item item = requireActiveItem(request.itemId());
        Location location = requireActiveLocation(request.locationId());

        String woNumber = "WO-%06d".formatted(workOrderRepository.nextWorkOrderNumber());
        WorkOrder saved = workOrderRepository.save(
                new WorkOrder(woNumber, item, location, request.requiredQuantity(), assignee, creator));
        log.info("Work order created: {} item={} qty={} assignedTo={} by={}",
                woNumber, item.getSku(), request.requiredQuantity(), assignee.getUsername(), creator.getUsername());
        return WorkOrderMapper.toResponse(workOrderRepository.findWithDetailsById(saved.getId()).orElseThrow());
    }

    @Transactional(readOnly = true)
    public PageResponse<WorkOrderResponse> list(Optional<WorkOrderStatus> status,
                                                Optional<Long> locationId,
                                                Pageable pageable) {
        Page<WorkOrderResponse> page = workOrderRepository
                .search(status.orElse(null), locationId.orElse(null), pageable)
                .map(WorkOrderMapper::toResponse);
        return PageResponse.from(page);
    }

    @Transactional(readOnly = true)
    public WorkOrderResponse get(Long id) {
        return WorkOrderMapper.toResponse(loadWithDetails(id));
    }

    @Transactional
    public WorkOrderResponse changeStatus(Long id, String rawStatus) {
        WorkOrderStatus target = parseStatus(rawStatus);
        WorkOrder wo = loadWithDetails(id);

        if (wo.getStatus() == target) {
            throw new InvalidStateTransitionException("Work order is already " + target);
        }
        if (!wo.getStatus().canTransitionTo(target)) {
            throw new InvalidStateTransitionException(
                    "Illegal work order transition: %s -> %s".formatted(wo.getStatus(), target));
        }
        wo.setStatus(target);
        if (target == WorkOrderStatus.COMPLETED) {
            wo.markCompletedAt(Instant.now());
        }
        workOrderRepository.save(wo);
        log.info("Work order {} transitioned to {}", wo.getWoNumber(), target);
        return WorkOrderMapper.toResponse(wo);
    }

    /**
     * Shortage is calculated here — on the backend — from the current stock position:
     * shortage = max(0, required - available). The frontend only renders the result.
     */
    @Transactional(readOnly = true)
    public MaterialAvailabilityResponse getMaterialAvailability(Long id) {
        WorkOrder wo = loadWithDetails(id);
        BigDecimal available = inventoryRepository
                .findByItemIdAndLocationId(wo.getItem().getId(), wo.getLocation().getId())
                .map(inv -> inv.getAvailableQty())
                .orElse(BigDecimal.ZERO);
        BigDecimal shortage = wo.getRequiredQuantity().subtract(available).max(BigDecimal.ZERO);
        boolean sufficient = shortage.signum() == 0;
        log.debug("Material availability for {}: available={} required={} shortage={}",
                wo.getWoNumber(), available, wo.getRequiredQuantity(), shortage);
        return new MaterialAvailabilityResponse(
                wo.getId(),
                wo.getWoNumber(),
                wo.getItem().getId(),
                wo.getItem().getSku(),
                wo.getLocation().getId(),
                wo.getLocation().getCode(),
                wo.getRequiredQuantity(),
                available,
                shortage,
                sufficient);
    }

    private WorkOrder loadWithDetails(Long id) {
        return workOrderRepository.findWithDetailsById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Work order", id));
    }

    private Item requireActiveItem(Long itemId) {
        Item item = itemRepository.findById(itemId)
                .orElseThrow(() -> ResourceNotFoundException.of("Item", itemId));
        if (!item.isActive()) {
            throw new BusinessRuleViolationException("Item is inactive: " + item.getSku());
        }
        return item;
    }

    private Location requireActiveLocation(Long locationId) {
        Location location = locationRepository.findById(locationId)
                .orElseThrow(() -> ResourceNotFoundException.of("Location", locationId));
        if (!location.isActive()) {
            throw new BusinessRuleViolationException("Location is inactive: " + location.getCode());
        }
        return location;
    }

    private User requireUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> ResourceNotFoundException.of("User", username));
    }

    static WorkOrderStatus parseStatus(String raw) {
        try {
            return WorkOrderStatus.valueOf(raw);
        } catch (IllegalArgumentException e) {
            throw new BusinessRuleViolationException("Unknown work order status: " + raw);
        }
    }
}
