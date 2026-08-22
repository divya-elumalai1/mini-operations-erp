package com.minierp.it;

import com.minierp.domain.ReferenceType;
import com.minierp.exception.BusinessRuleViolationException;
import com.minierp.exception.InsufficientStockException;
import com.minierp.repository.InventoryRepository;
import com.minierp.repository.InventoryTransactionRepository;
import com.minierp.service.InventoryLedger;
import com.minierp.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Inventory invariants: receipts create ledger rows, reservations can never exceed
 * availability, duplicate transactions are rejected, and the audit trail is complete.
 */
class InventoryIT extends AbstractIntegrationTest {

    @Autowired
    private InventoryRepository inventoryRepository;
    @Autowired
    private InventoryTransactionRepository transactionRepository;
    @Autowired
    private InventoryLedger ledger;

    private static final BigDecimal ZERO = BigDecimal.ZERO;

    @Test
    @DisplayName("goods receipt increases physical stock and writes a GOODS_RECEIPT ledger row")
    void receiptHappyPath() throws Exception {
        mockMvc.perform(post("/api/inventory/receipts")
                        .with(user("ops").roles("OPERATIONS_USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"itemId":2,"locationId":1,"quantity":30,"notes":"supplier delivery",
                                 "idempotencyKey":"rcpt-abc"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.physicalQty").value(150.0))
                .andExpect(jsonPath("$.reservedQty").value(0.0))
                .andExpect(jsonPath("$.availableQty").value(150.0));

        assertThat(transactionRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("receipt into a brand-new (item, location) creates the inventory row via upsert")
    void receiptCreatesNewInventoryRow() throws Exception {
        mockMvc.perform(post("/api/inventory/receipts")
                        .with(user("ops").roles("OPERATIONS_USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"itemId\":3,\"locationId\":3,\"quantity\":9}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.physicalQty").value(9.0));
    }

    @Test
    @DisplayName("duplicate idempotency key is rejected and stock unchanged (no double receipt)")
    void duplicateIdempotencyKeyRejected() throws Exception {
        String body = "{\"itemId\":1,\"locationId\":1,\"quantity\":10,\"idempotencyKey\":\"same-key-1\"}";
        mockMvc.perform(post("/api/inventory/receipts")
                        .with(user("ops").roles("OPERATIONS_USER"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/inventory/receipts")
                        .with(user("ops").roles("OPERATIONS_USER"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("DUPLICATE_RESOURCE"));

        BigDecimal physical = inventoryRepository.findByItemIdAndLocationId(1L, 1L).orElseThrow()
                .getPhysicalQty();
        assertThat(physical).isEqualByComparingTo("510"); // 500 + 10 once, not twice
        assertThat(transactionRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("Mandatory Test 1: reserving more than available (11 > 10) is rejected, inventory unchanged")
    void cannotReserveMoreThanAvailable() {
        // item 4 @ location 2 has available = 10
        assertThrows(InsufficientStockException.class,
                () -> inTx(() -> ledger.reserve(4L, 2L, BigDecimal.valueOf(11),
                        ReferenceType.CUSTOMER_ORDER, 999L, 1L)));

        var inv = inventoryRepository.findByItemIdAndLocationId(4L, 2L).orElseThrow();
        assertThat(inv.getReservedQty()).isEqualByComparingTo(ZERO);
        assertThat(inv.getPhysicalQty()).isEqualByComparingTo("10");
    }

    @Test
    @DisplayName("reserving exactly the full available quantity succeeds")
    void reserveExactAvailableSucceeds() {
        inTx(() -> ledger.reserve(4L, 2L, BigDecimal.TEN, ReferenceType.CUSTOMER_ORDER, 1L, 1L));

        var inv = inventoryRepository.findByItemIdAndLocationId(4L, 2L).orElseThrow();
        assertThat(inv.getAvailableQty()).isEqualByComparingTo(ZERO);
        assertThat(inv.getReservedQty()).isEqualByComparingTo("10");
    }

    @Test
    @DisplayName("release returns reserved quantity to available")
    void releaseRestoresAvailability() {
        inTx(() -> {
            ledger.reserve(4L, 2L, BigDecimal.TEN, ReferenceType.CUSTOMER_ORDER, 1L, 1L);
            ledger.release(4L, 2L, BigDecimal.TEN, ReferenceType.CUSTOMER_ORDER, 1L, "cancel", 1L);
        });

        var inv = inventoryRepository.findByItemIdAndLocationId(4L, 2L).orElseThrow();
        assertThat(inv.getReservedQty()).isEqualByComparingTo(ZERO);
        assertThat(inv.getAvailableQty()).isEqualByComparingTo("10");
    }

    @Test
    @DisplayName("releasing an unknown reservation is rejected")
    void releaseWithoutReservationFails() {
        assertThrows(BusinessRuleViolationException.class,
                () -> inTx(() -> ledger.release(4L, 2L, BigDecimal.ONE,
                        ReferenceType.CUSTOMER_ORDER, 1L, null, 1L)));
    }

    @Test
    @DisplayName("ledger endpoint lists transactions newest first with actor names")
    void ledgerEndpointReturnsTransactions() throws Exception {
        mockMvc.perform(post("/api/inventory/receipts")
                        .with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"itemId\":4,\"locationId\":1,\"quantity\":5,\"idempotencyKey\":\"led-1\"}"))
                .andExpect(status().isCreated());

        MvcResult result = mockMvc.perform(get("/api/inventory/transactions")
                        .with(user("sales").roles("SALES_USER")))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("\"txType\":\"GOODS_RECEIPT\"");
        assertThat(body).contains("\"performedByUsername\":\"admin\"");
        assertThat(body).contains("\"totalElements\":1");
    }
}
