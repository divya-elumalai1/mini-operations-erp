package com.minierp.it;

import com.minierp.domain.ReferenceType;
import com.minierp.exception.InsufficientStockException;
import com.minierp.repository.CustomerOrderRepository;
import com.minierp.repository.InventoryRepository;
import com.minierp.repository.InventoryTransactionRepository;
import com.minierp.service.InventoryLedger;
import com.minierp.service.OrderService;
import com.minierp.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Customer orders + reservation correctness, including the assignment's core
 * concurrency scenario executed with real threads against the real database.
 */
class OrderReservationIT extends AbstractIntegrationTest {

    @Autowired
    private OrderService orderService;
    @Autowired
    private InventoryRepository inventoryRepository;
    @Autowired
    private CustomerOrderRepository orderRepository;
    @Autowired
    private InventoryTransactionRepository transactionRepository;
    @Autowired
    private InventoryLedger ledger;

    @Test
    @DisplayName("creating an order reserves stock: reserved up, available down, ledger rows written")
    void createOrderReservesStock() throws Exception {
        mockMvc.perform(post("/api/orders")
                        .with(SecurityMockMvcRequestPostProcessors.user("sales").roles("SALES_USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerName":"Acme Corp",
                                 "lines":[{"itemId":3,"locationId":1,"quantity":10}]}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderNumber").value("SO-000001"))
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.lines[0].quantity").value(10.0));

        var inv = inventoryRepository.findByItemIdAndLocationId(3L, 1L).orElseThrow();
        assertThat(inv.getPhysicalQty()).isEqualByComparingTo("70");
        assertThat(inv.getReservedQty()).isEqualByComparingTo("10");
        assertThat(inv.getAvailableQty()).isEqualByComparingTo("60");
        assertThat(transactionRepository.count()).isEqualTo(1); // RESERVE row
    }

    @Test
    @DisplayName("Mandatory Test 1 (HTTP): ordering more than available is rejected and nothing persists")
    void cannotReserveMoreThanAvailable() throws Exception {
        mockMvc.perform(post("/api/orders")
                        .with(SecurityMockMvcRequestPostProcessors.user("sales").roles("SALES_USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerName":"Acme",
                                 "lines":[{"itemId":4,"locationId":2,"quantity":11}]}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("INSUFFICIENT_STOCK"));

        var inv = inventoryRepository.findByItemIdAndLocationId(4L, 2L).orElseThrow();
        assertThat(inv.getReservedQty()).isEqualByComparingTo("0");
        assertThat(orderRepository.count()).isEqualTo(0);
        assertThat(transactionRepository.count()).isEqualTo(0);
    }

    @Test
    @DisplayName("multi-line order with one failing line rolls back EVERYTHING")
    void multiLineRollbackOnFailure() {
        var request = new com.minierp.dto.OrderCreateRequest("Acme", List.of(
                new com.minierp.dto.OrderCreateRequest.OrderLineRequest(3L, 1L, BigDecimal.TEN),   // fits
                new com.minierp.dto.OrderCreateRequest.OrderLineRequest(4L, 2L, BigDecimal.valueOf(50)))); // 10 only

        assertThrows(InsufficientStockException.class,
                () -> orderService.createOrderWithReservation(request, "sales"));

        assertThat(orderRepository.count()).isEqualTo(0);                       // header gone
        assertThat(transactionRepository.count()).isEqualTo(0);                 // no ledger rows
        assertThat(inventoryRepository.findByItemIdAndLocationId(3L, 1L).orElseThrow().getReservedQty())
                .isEqualByComparingTo("0");                                     // line 1 released by rollback
    }

    @Test
    @DisplayName("duplicate lines for the same item+location are merged before reserving")
    void duplicateLinesAreMerged() {
        var request = new com.minierp.dto.OrderCreateRequest("Acme", List.of(
                new com.minierp.dto.OrderCreateRequest.OrderLineRequest(4L, 2L, BigDecimal.valueOf(6)),
                new com.minierp.dto.OrderCreateRequest.OrderLineRequest(4L, 2L, BigDecimal.valueOf(4))));

        var response = orderService.createOrderWithReservation(request, "sales");

        assertThat(response.lines()).hasSize(1);
        assertThat(response.lines().get(0).quantity()).isEqualByComparingTo("10");
        assertThat(inventoryRepository.findByItemIdAndLocationId(4L, 2L).orElseThrow().getReservedQty())
                .isEqualByComparingTo("10");
    }

    /**
     * THE concurrency test. Available stock = 70. Eight customers simultaneously try
     * to reserve 10 each (80 total demand). Exactly seven may succeed; the database
     * must end with reserved == physical == 70 and available == 0.
     */
    @Test
    @DisplayName("concurrent reservations of the last stock: exactly one set succeeds, never oversold")
    void concurrentReservationsNeverOversell() throws Exception {
        int threads = 8;
        BigDecimal perOrder = BigDecimal.TEN;
        var pool = Executors.newFixedThreadPool(threads);
        var start = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            final String customer = "Customer-" + i;
            results.add(pool.submit(() -> {
                start.await();
                var request = new com.minierp.dto.OrderCreateRequest(customer, List.of(
                        new com.minierp.dto.OrderCreateRequest.OrderLineRequest(3L, 1L, perOrder)));
                try {
                    orderService.createOrderWithReservation(request, "sales");
                    return true;
                } catch (InsufficientStockException expected) {
                    return false;
                }
            }));
        }
        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(60, TimeUnit.SECONDS)).isTrue();

        long succeeded = results.stream().filter(f -> {
            try {
                return f.get();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }).count();

        assertThat(succeeded).as("exactly floor(70/10)=7 orders may succeed").isEqualTo(7);

        var inv = inventoryRepository.findByItemIdAndLocationId(3L, 1L).orElseThrow();
        assertThat(inv.getReservedQty()).isEqualByComparingTo("70");
        assertThat(inv.getAvailableQty()).isEqualByComparingTo("0");
        assertThat(orderRepository.count()).isEqualTo(7);
        // Ledger completeness: exactly 7 RESERVE rows, all referencing real orders.
        long reserveRows = transactionRepository.findAll().stream()
                .filter(t -> t.getTxType() == com.minierp.domain.InventoryTxType.RESERVE)
                .count();
        assertThat(reserveRows).isEqualTo(7);
    }

    @Test
    @DisplayName("two racers for the last 10 units: exactly one wins (classic A/B case)")
    void twoRacersForLastUnitsExactlyOneWins() throws Exception {
        // item 4 @ loc 2 has exactly 10 units left.
        int threads = 2;
        var pool = Executors.newFixedThreadPool(threads);
        var start = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            results.add(pool.submit(() -> {
                start.await();
                try {
                    inTx(() -> ledger.reserve(4L, 2L, BigDecimal.TEN,
                            ReferenceType.CUSTOMER_ORDER, 42L, 1L));
                    return true;
                } catch (InsufficientStockException e) {
                    return false;
                }
            }));
        }
        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

        long wins = results.stream().filter(f -> {
            try {
                return f.get();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }).count();

        assertThat(wins).isEqualTo(1);
        assertThat(inventoryRepository.findByItemIdAndLocationId(4L, 2L).orElseThrow().getReservedQty())
                .isEqualByComparingTo("10");
    }

    private Long nullSafe() {
        return 42L;
    }
}
