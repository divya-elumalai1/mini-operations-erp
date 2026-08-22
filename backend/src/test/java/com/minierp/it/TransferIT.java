package com.minierp.it;

import com.minierp.repository.InventoryRepository;
import com.minierp.service.TransferService;
import com.minierp.dto.TransferCreateRequest;
import com.minierp.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Transfer lifecycle: Mandatory Tests 2, 3 and 4 plus duplicate-receipt protection.
 */
class TransferIT extends AbstractIntegrationTest {

    @Autowired
    private TransferService transferService;
    @Autowired
    private InventoryRepository inventoryRepository;
    @Autowired
    private com.minierp.service.InventoryLedger ledger;

    @Test
    @DisplayName("Mandatory Test 2: dispatching more than available (11 > 10) is rejected, source unchanged")
    void dispatchInsufficientStockRejected() {
        // item 4 @ location 2: available = 10
        var transfer = transferService.create(
                new TransferCreateRequest(2L, 1L, 4L, BigDecimal.valueOf(11)), "ops");

        assertThrows(com.minierp.exception.InsufficientStockException.class,
                () -> transferService.dispatch(transfer.id(), "ops"));

        var source = inventoryRepository.findByItemIdAndLocationId(4L, 2L).orElseThrow();
        assertThat(source.getPhysicalQty()).isEqualByComparingTo("10");   // untouched
        assertThat(source.getReservedQty()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("Mandatory Test 3: source decreases at dispatch; destination increases only after receipt")
    void destinationChangesOnlyAfterReceipt() throws Exception {
        // seeded TRF-000001: 50 rods, BLR-WH01 -> BLR-SHOP01
        mockMvc.perform(post("/api/transfers/1/dispatch")
                        .with(user("ops").roles("OPERATIONS_USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISPATCHED"));

        assertThat(physical(1, 1)).isEqualByComparingTo("450"); // source decreased
        assertThat(physical(1, 2)).isEqualByComparingTo("0");   // destination UNCHANGED

        mockMvc.perform(post("/api/transfers/1/receive")
                        .with(user("ops").roles("OPERATIONS_USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RECEIVED"));

        assertThat(physical(1, 2)).isEqualByComparingTo("50");  // destination increased now
        assertThat(physical(1, 1)).isEqualByComparingTo("450");
    }

    @Test
    @DisplayName("Mandatory Test 4: the same transfer cannot be received twice")
    void sameTransferCannotBeReceivedTwice() throws Exception {
        mockMvc.perform(post("/api/transfers/1/dispatch")
                        .with(user("ops").roles("OPERATIONS_USER")))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/transfers/1/receive")
                        .with(user("ops").roles("OPERATIONS_USER")))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/transfers/1/receive")
                        .with(user("ops").roles("OPERATIONS_USER")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("INVALID_STATE_TRANSITION"));

        assertThat(physical(1, 2)).isEqualByComparingTo("50"); // increased exactly once
    }

    @Test
    @DisplayName("receiving a REQUESTED (never dispatched) transfer is rejected")
    void receiveWithoutDispatchRejected() throws Exception {
        mockMvc.perform(post("/api/transfers/1/receive")
                        .with(user("ops").roles("OPERATIONS_USER")))
                .andExpect(status().isConflict());
        assertThat(physical(1, 2)).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("dispatching a RECEIVED transfer is rejected")
    void dispatchAfterReceiveRejected() throws Exception {
        transferService.dispatch(1L, "ops");
        transferService.receive(1L, "ops");
        assertThrows(com.minierp.exception.InvalidStateTransitionException.class,
                () -> transferService.dispatch(1L, "ops"));
    }

    @Test
    @DisplayName("transfer with source == destination is rejected")
    void sourceMustDifferFromDestination() throws Exception {
        mockMvc.perform(post("/api/transfers")
                        .with(SecurityMockMvcRequestPostProcessors.user("ops").roles("OPERATIONS_USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourceLocationId\":1,\"destinationLocationId\":1,\"itemId\":1,\"quantity\":5}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("BUSINESS_RULE_VIOLATION"));
    }

    @Test
    @DisplayName("non-positive quantity is rejected with validation error")
    void nonPositiveQuantityRejected() throws Exception {
        mockMvc.perform(post("/api/transfers")
                        .with(user("ops").roles("OPERATIONS_USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourceLocationId\":1,\"destinationLocationId\":2,\"itemId\":1,\"quantity\":-3}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].field").value("quantity"));
    }

    @Test
    @DisplayName("reserved stock is protected: cannot dispatch unreserved availability only")
    void reservedStockNotShippable() {
        // Reserve 500 of item 1 @ loc 1 (its full stock), leaving 0 available for dispatch.
        inTx(() -> ledger.reserve(1L, 1L, BigDecimal.valueOf(500),
                com.minierp.domain.ReferenceType.CUSTOMER_ORDER, 1L, 1L));

        var transfer = transferService.create(
                new TransferCreateRequest(1L, 2L, 1L, BigDecimal.valueOf(100)), "ops");

        assertThrows(com.minierp.exception.InsufficientStockException.class,
                () -> transferService.dispatch(transfer.id(), "ops"));

        assertThat(inventoryRepository.findByItemIdAndLocationId(1L, 1L).orElseThrow().getPhysicalQty())
                .isEqualByComparingTo("500");
    }

    /** Missing inventory rows count as zero stock (e.g. destination never stocked yet). */
    private BigDecimal physical(long itemId, long locationId) {
        return inventoryRepository.findByItemIdAndLocationId(itemId, locationId)
                .map(inv -> inv.getPhysicalQty())
                .orElse(BigDecimal.ZERO);
    }
}
