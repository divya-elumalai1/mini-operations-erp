package com.minierp.service;

import com.minierp.domain.*;
import com.minierp.dto.PageResponse;
import com.minierp.dto.TransferCreateRequest;
import com.minierp.dto.TransferResponse;
import com.minierp.exception.BusinessRuleViolationException;
import com.minierp.exception.InvalidStateTransitionException;
import com.minierp.exception.ResourceNotFoundException;
import com.minierp.mapper.OrderTransferMapper;
import com.minierp.repository.InternalTransferRepository;
import com.minierp.repository.ItemRepository;
import com.minierp.repository.LocationRepository;
import com.minierp.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class TransferService {

    private static final Logger log = LoggerFactory.getLogger(TransferService.class);

    private final InternalTransferRepository transferRepository;
    private final ItemRepository itemRepository;
    private final LocationRepository locationRepository;
    private final UserRepository userRepository;
    private final InventoryLedger ledger;
    private final EntityManager entityManager;

    public TransferService(InternalTransferRepository transferRepository,
                           ItemRepository itemRepository,
                           LocationRepository locationRepository,
                           UserRepository userRepository,
                           InventoryLedger ledger,
                           EntityManager entityManager) {
        this.transferRepository = transferRepository;
        this.itemRepository = itemRepository;
        this.locationRepository = locationRepository;
        this.userRepository = userRepository;
        this.ledger = ledger;
        this.entityManager = entityManager;
    }

    @Transactional
    public TransferResponse create(TransferCreateRequest request, String createdByUsername) {
        User creator = requireUser(createdByUsername);
        if (request.sourceLocationId().equals(request.destinationLocationId())) {
            throw new BusinessRuleViolationException("Source and destination locations must differ");
        }
        Location source = requireActiveLocation(request.sourceLocationId());
        Location destination = requireActiveLocation(request.destinationLocationId());
        Item item = requireActiveItem(request.itemId());

        String number = "TRF-%06d".formatted(transferRepository.nextTransferNumber());
        InternalTransfer saved = transferRepository.save(
                new InternalTransfer(number, source, destination, item, request.quantity(), creator));
        log.info("Transfer created: {} item={} qty={} {} -> {} by={}",
                number, item.getSku(), request.quantity(),
                source.getCode(), destination.getCode(), creator.getUsername());
        return OrderTransferMapper.toResponse(loadWithDetails(saved.getId()));
    }

    /**
     * Moves stock OUT of the source location. The destination is intentionally NOT
     * credited here — goods are in transit until receive() is called. Atomic unit:
     * source deduction + status guard + ledger row all commit or roll back together.
     */
    @Transactional
    public TransferResponse dispatch(Long id, String username) {
        User performer = requireUser(username);
        InternalTransfer transfer = loadWithDetails(id);
        if (!transfer.isDispatchable()) {
            throw new InvalidStateTransitionException(
                    "Transfer %s cannot be dispatched from status %s"
                            .formatted(transfer.getTransferNumber(), transfer.getStatus()));
        }
        // Fails with INSUFFICIENT_STOCK (rolling everything back) if the source
        // does not currently hold enough unreserved stock.
        ledger.shipStock(transfer.getItem().getId(), transfer.getSourceLocation().getId(),
                transfer.getQuantity(), transfer.getId(), performer.getId());

        int updated = transferRepository.markDispatched(id, performer.getId());
        if (updated == 0) {
            // A concurrent dispatch won the race; our stock deduction rolls back too.
            throw new InvalidStateTransitionException(
                    "Transfer %s was dispatched concurrently".formatted(transfer.getTransferNumber()));
        }
        log.info("Transfer dispatched: {} by {}", transfer.getTransferNumber(), username);
        return freshResponse(id);
    }

    /**
     * Credits the destination and closes the transfer. Single-shot: the status-guarded
     * UPDATE makes a second receipt impossible — sequential duplicates fail the
     * isReceivable check, concurrent duplicates lose the guarded update (0 rows).
     */
    @Transactional
    public TransferResponse receive(Long id, String username) {
        User performer = requireUser(username);
        InternalTransfer transfer = loadWithDetails(id);
        if (!transfer.isReceivable()) {
            throw new InvalidStateTransitionException(
                    "Transfer %s cannot be received from status %s (already received or not dispatched)"
                            .formatted(transfer.getTransferNumber(), transfer.getStatus()));
        }
        ledger.receiveStock(transfer.getItem().getId(), transfer.getDestinationLocation().getId(),
                transfer.getQuantity(), transfer.getId(), performer.getId());

        int updated = transferRepository.markReceived(id, performer.getId());
        if (updated == 0) {
            // Concurrent duplicate receipt lost the race; the destination credit rolls back.
            throw new InvalidStateTransitionException(
                    "Transfer %s was already received".formatted(transfer.getTransferNumber()));
        }
        log.info("Transfer received: {} by {}", transfer.getTransferNumber(), username);
        return freshResponse(id);
    }

    /**
     * The status guard updates bypass the persistence context; detach everything and
     * re-read so the returned state reflects what was actually written.
     */
    private TransferResponse freshResponse(Long id) {
        entityManager.flush();
        entityManager.clear();
        return OrderTransferMapper.toResponse(loadWithDetails(id));
    }

    @Transactional(readOnly = true)
    public PageResponse<TransferResponse> list(Optional<TransferStatus> status, Pageable pageable) {
        Page<TransferResponse> page = transferRepository
                .search(status.orElse(null), pageable)
                .map(OrderTransferMapper::toResponse);
        return PageResponse.from(page);
    }

    @Transactional(readOnly = true)
    public TransferResponse get(Long id) {
        return OrderTransferMapper.toResponse(loadWithDetails(id));
    }

    private InternalTransfer loadWithDetails(Long id) {
        return transferRepository.findWithDetailsById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Transfer", id));
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
}
