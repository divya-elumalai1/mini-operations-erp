package com.minierp.service;

import com.minierp.domain.Item;
import com.minierp.domain.InventoryTxType;
import com.minierp.domain.Location;
import com.minierp.domain.ReferenceType;
import com.minierp.domain.User;
import com.minierp.dto.*;
import com.minierp.exception.BusinessRuleViolationException;
import com.minierp.exception.ResourceNotFoundException;
import com.minierp.mapper.InventoryMapper;
import com.minierp.repository.InventoryRepository;
import com.minierp.repository.InventoryTransactionRepository;
import com.minierp.repository.ItemRepository;
import com.minierp.repository.LocationRepository;
import com.minierp.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class InventoryService {

    private static final Logger log = LoggerFactory.getLogger(InventoryService.class);

    private final InventoryRepository inventoryRepository;
    private final InventoryTransactionRepository transactionRepository;
    private final ItemRepository itemRepository;
    private final LocationRepository locationRepository;
    private final UserRepository userRepository;
    private final InventoryLedger ledger;

    public InventoryService(InventoryRepository inventoryRepository,
                            InventoryTransactionRepository transactionRepository,
                            ItemRepository itemRepository,
                            LocationRepository locationRepository,
                            UserRepository userRepository,
                            InventoryLedger ledger) {
        this.inventoryRepository = inventoryRepository;
        this.transactionRepository = transactionRepository;
        this.itemRepository = itemRepository;
        this.locationRepository = locationRepository;
        this.userRepository = userRepository;
        this.ledger = ledger;
    }

    @Transactional(readOnly = true)
    public PageResponse<InventoryResponse> searchInventory(Optional<Long> itemId,
                                                           Optional<Long> locationId,
                                                           Pageable pageable) {
        Page<InventoryResponse> page = inventoryRepository
                .search(itemId.orElse(null), locationId.orElse(null), pageable)
                .map(InventoryMapper::toResponse);
        return PageResponse.from(page);
    }

    @Transactional(readOnly = true)
    public PageResponse<InventoryTransactionResponse> searchTransactions(Optional<Long> itemId,
                                                                          Optional<Long> locationId,
                                                                          Optional<String> type,
                                                                          Pageable pageable) {
        InventoryTxType txType = type.map(InventoryService::parseType).orElse(null);
        Page<InventoryTransactionResponse> page = transactionRepository
                .search(itemId.orElse(null), locationId.orElse(null), txType, pageable)
                .map(InventoryMapper::toResponse);
        return PageResponse.from(page);
    }

    /**
     * Goods receipt: adds physical stock and writes the audit row atomically.
     * A repeated idempotencyKey is rejected by the unique index, so stock can
     * never be received twice for one request key.
     */
    @Transactional
    public InventoryResponse receipt(ReceiptRequest request, String username) {
        Item item = requireActiveItem(request.itemId());
        Location location = requireActiveLocation(request.locationId());
        User performer = requireUser(username);

        ledger.receipt(item.getId(), location.getId(), request.batchCode(), request.quantity(),
                ReferenceType.MANUAL, null, request.notes(), request.idempotencyKey(),
                performer.getId());

        InventoryResponse response = inventoryRepository
                .findByItemIdAndLocationId(item.getId(), location.getId())
                .map(InventoryMapper::toResponse)
                .orElseThrow();
        log.info("Goods receipt completed: user={} item={} location={} qty={}",
                username, item.getSku(), location.getCode(), request.quantity());
        return response;
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

    private static InventoryTxType parseType(String raw) {
        try {
            return InventoryTxType.valueOf(raw);
        } catch (IllegalArgumentException e) {
            throw new BusinessRuleViolationException("Unknown transaction type: " + raw);
        }
    }
}
