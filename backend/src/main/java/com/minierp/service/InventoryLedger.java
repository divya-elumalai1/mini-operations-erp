package com.minierp.service;

import com.minierp.domain.*;
import com.minierp.exception.BusinessRuleViolationException;
import com.minierp.exception.DuplicateResourceException;
import com.minierp.exception.InsufficientStockException;
import com.minierp.repository.BatchRepository;
import com.minierp.repository.InventoryRepository;
import com.minierp.repository.InventoryTransactionRepository;
import com.minierp.repository.ItemRepository;
import com.minierp.repository.LocationRepository;
import com.minierp.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * THE single gateway for mutating inventory quantities. Every stock movement in the
 * system flows through this component, which guarantees:
 *
 * 1. positive-quantity discipline,
 * 2. atomic conditional updates (never read-check-write) so concurrent operations
 *    cannot oversell — see docs/CONCURRENCY.md,
 * 3. an audit ledger row for every movement, written in the caller's transaction.
 *
 * Propagation.MANDATORY documents that callers own the transaction boundary.
 */
@Component
public class InventoryLedger {

    private static final Logger log = LoggerFactory.getLogger(InventoryLedger.class);

    private final InventoryRepository inventoryRepository;
    private final InventoryTransactionRepository transactionRepository;
    private final ItemRepository itemRepository;
    private final LocationRepository locationRepository;
    private final BatchRepository batchRepository;
    private final UserRepository userRepository;

    public InventoryLedger(InventoryRepository inventoryRepository,
                           InventoryTransactionRepository transactionRepository,
                           ItemRepository itemRepository,
                           LocationRepository locationRepository,
                           BatchRepository batchRepository,
                           UserRepository userRepository) {
        this.inventoryRepository = inventoryRepository;
        this.transactionRepository = transactionRepository;
        this.itemRepository = itemRepository;
        this.locationRepository = locationRepository;
        this.batchRepository = batchRepository;
        this.userRepository = userRepository;
    }

    /** Adds physical stock (goods receipt). Idempotency key, when supplied, blocks duplicates. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void receipt(Long itemId, Long locationId, String batchCode, BigDecimal quantity,
                        ReferenceType referenceType, Long referenceId,
                        String notes, String idempotencyKey, Long performedByUserId) {
        requirePositive(quantity, "receipt quantity");
        Item item = itemRepository.getReferenceById(itemId);
        Location location = locationRepository.getReferenceById(locationId);
        Batch batch = resolveBatch(item, batchCode);
        User performer = userRepository.getReferenceById(performedByUserId);

        int updated = inventoryRepository.addPhysicalStockUpsert(itemId, locationId, quantity);
        persistLedger(new InventoryTransaction(item, location, batch, InventoryTxType.GOODS_RECEIPT,
                referenceType, referenceId, quantity, BigDecimal.ZERO,
                notes, idempotencyKey, performer));
        logStockEvent("Goods receipt", itemId, locationId, quantity, "physical", updated);
    }

    /**
     * Reserves stock for later fulfillment. Atomic guarded update: when two transactions
     * race for the last units, the loser's UPDATE matches zero rows and throws here,
     * rolling the whole business transaction back.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void reserve(Long itemId, Long locationId, BigDecimal quantity,
                        ReferenceType referenceType, Long referenceId, Long performedByUserId) {
        requirePositive(quantity, "reservation quantity");
        int updated = inventoryRepository.reserve(itemId, locationId, quantity);
        if (updated == 0) {
            throw new InsufficientStockException(
                    "Cannot reserve %s of item %d at location %d: insufficient available stock"
                            .formatted(quantity.toPlainString(), itemId, locationId));
        }
        persistLedger(ledgerRow(itemId, locationId, null, InventoryTxType.RESERVE,
                referenceType, referenceId, BigDecimal.ZERO, quantity, performedByUserId, null));
        log.info("Stock reserved: item={} location={} qty={} reference={}/{}",
                itemId, locationId, quantity, referenceType, referenceId);
    }

    /** Releases a previously made reservation (e.g. order cancellation path). */
    @Transactional(propagation = Propagation.MANDATORY)
    public void release(Long itemId, Long locationId, BigDecimal quantity,
                        ReferenceType referenceType, Long referenceId,
                        String notes, Long performedByUserId) {
        requirePositive(quantity, "release quantity");
        int updated = inventoryRepository.releaseReservation(itemId, locationId, quantity);
        if (updated == 0) {
            throw new BusinessRuleViolationException(
                    "Cannot release %s of item %d at location %d: nothing (or not enough) reserved there"
                            .formatted(quantity.toPlainString(), itemId, locationId));
        }
        persistLedger(ledgerRow(itemId, locationId, null, InventoryTxType.RELEASE,
                referenceType, referenceId, BigDecimal.ZERO, quantity.negate(), performedByUserId, notes));
        log.info("Reservation released: item={} location={} qty={} reference={}/{}",
                itemId, locationId, quantity, referenceType, referenceId);
    }

    /** Dispatch side of a transfer: decreases unreserved physical stock at the source. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void shipStock(Long itemId, Long sourceLocationId, BigDecimal quantity,
                          Long transferId, Long performedByUserId) {
        requirePositive(quantity, "dispatch quantity");
        int updated = inventoryRepository.consumeAvailableStock(itemId, sourceLocationId, quantity);
        if (updated == 0) {
            throw new InsufficientStockException(
                    "Cannot dispatch %s of item %d from location %d: insufficient available stock"
                            .formatted(quantity.toPlainString(), itemId, sourceLocationId));
        }
        persistLedger(ledgerRow(itemId, sourceLocationId, null, InventoryTxType.TRANSFER_OUT,
                ReferenceType.INTERNAL_TRANSFER, transferId, quantity.negate(), BigDecimal.ZERO,
                performedByUserId, null));
        log.info("Transfer dispatched: item={} source={} qty={} transfer={}",
                itemId, sourceLocationId, quantity, transferId);
    }

    /** Receipt side of a transfer: increases physical stock at the destination. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void receiveStock(Long itemId, Long destinationLocationId, BigDecimal quantity,
                             Long transferId, Long performedByUserId) {
        requirePositive(quantity, "receive quantity");
        inventoryRepository.addPhysicalStockUpsert(itemId, destinationLocationId, quantity);
        persistLedger(ledgerRow(itemId, destinationLocationId, null, InventoryTxType.TRANSFER_IN,
                ReferenceType.INTERNAL_TRANSFER, transferId, quantity, BigDecimal.ZERO,
                performedByUserId, null));
        log.info("Transfer received: item={} destination={} qty={} transfer={}",
                itemId, destinationLocationId, quantity, transferId);
    }

    private void persistLedger(InventoryTransaction tx) {
        try {
            transactionRepository.saveAndFlush(tx);
        } catch (DataIntegrityViolationException e) {
            throw new DuplicateResourceException(
                    "Duplicate inventory transaction rejected (idempotency key already used)");
        }
    }

    private InventoryTransaction ledgerRow(Long itemId, Long locationId, Batch batch,
                                           InventoryTxType type, ReferenceType refType, Long refId,
                                           BigDecimal deltaPhysical, BigDecimal deltaReserved,
                                           Long performedByUserId, String notes) {
        return new InventoryTransaction(
                itemRepository.getReferenceById(itemId),
                locationRepository.getReferenceById(locationId),
                batch,
                type, refType, refId,
                deltaPhysical, deltaReserved,
                notes, null,
                userRepository.getReferenceById(performedByUserId));
    }

    private Batch resolveBatch(Item item, String batchCode) {
        if (batchCode == null || batchCode.isBlank()) {
            return null;
        }
        return batchRepository.findByItemIdAndBatchCode(item.getId(), batchCode)
                .orElseGet(() -> batchRepository.save(new Batch(item, batchCode)));
    }

    private void requirePositive(BigDecimal quantity, String what) {
        if (quantity == null || quantity.signum() <= 0) {
            throw new BusinessRuleViolationException(what + " must be greater than 0");
        }
    }

    private void logStockEvent(String action, Long itemId, Long locationId, BigDecimal qty,
                               String kind, int rowsUpdated) {
        log.info("{}: item={} location={} {}+={} rows={}", action, itemId, locationId, kind, qty, rowsUpdated);
    }
}
