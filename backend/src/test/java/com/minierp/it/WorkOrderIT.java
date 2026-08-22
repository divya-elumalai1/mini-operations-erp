package com.minierp.it;

import com.minierp.dto.MaterialAvailabilityResponse;
import com.minierp.dto.WorkOrderResponse;
import com.minierp.repository.InventoryRepository;
import com.minierp.service.WorkOrderService;
import com.minierp.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Work order lifecycle and backend shortage calculation.
 */
class WorkOrderIT extends AbstractIntegrationTest {

    @Autowired
    private WorkOrderService workOrderService;
    @Autowired
    private InventoryRepository inventoryRepository;

    @Test
    @DisplayName("admin creates a work order; number is generated server-side")
    void createWorkOrder() throws Exception {
        mockMvc.perform(post("/api/work-orders")
                        .with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"itemId\":1,\"locationId\":1,\"assignedUserId\":2,\"requiredQuantity\":100}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.woNumber").value("WO-000002"))
                .andExpect(jsonPath("$.status").value("ASSIGNED"))
                .andExpect(jsonPath("$.assignedUsername").value("ops"));
    }

    @Test
    @DisplayName("shortage is calculated by the backend: required 100, available 70 -> shortage 30")
    void shortageCalculatedByBackend() {
        // item 3 @ location 1: available = 70 (seeded). Create WO requiring 100 there.
        WorkOrderResponse wo = workOrderService.create(
                new com.minierp.dto.WorkOrderCreateRequest(3L, 1L, 2L, BigDecimal.valueOf(100)),
                "admin");

        MaterialAvailabilityResponse availability = workOrderService.getMaterialAvailability(wo.id());
        assertThat(availability.requiredQuantity()).isEqualByComparingTo("100");
        assertThat(availability.availableQuantity()).isEqualByComparingTo("70");
        assertThat(availability.shortageQuantity()).isEqualByComparingTo("30");
        assertThat(availability.sufficient()).isFalse();
    }

    @Test
    @DisplayName("sufficient material reports zero shortage")
    void sufficientMaterialReportsNoShortage() {
        // item 1 @ location 1: available = 500; require 100
        WorkOrderResponse wo = workOrderService.create(
                new com.minierp.dto.WorkOrderCreateRequest(1L, 1L, 2L, BigDecimal.valueOf(100)),
                "admin");

        MaterialAvailabilityResponse availability = workOrderService.getMaterialAvailability(wo.id());
        assertThat(availability.shortageQuantity()).isEqualByComparingTo("0");
        assertThat(availability.sufficient()).isTrue();
    }

    @Test
    @DisplayName("legal transitions ASSIGNED -> IN_PROGRESS -> COMPLETED succeed")
    void legalTransitionsSucceed() throws Exception {
        mockMvc.perform(patch("/api/work-orders/1/status")
                        .with(user("ops").roles("OPERATIONS_USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"IN_PROGRESS\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));

        mockMvc.perform(patch("/api/work-orders/1/status")
                        .with(user("ops").roles("OPERATIONS_USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"COMPLETED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.completedAt").isNotEmpty());
    }

    @Test
    @DisplayName("illegal transitions are rejected (COMPLETED is terminal)")
    void illegalTransitionsRejected() throws Exception {
        mockMvc.perform(patch("/api/work-orders/1/status")
                        .with(SecurityMockMvcRequestPostProcessors.user("ops").roles("OPERATIONS_USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"COMPLETED\"}"))          // ASSIGNED -> COMPLETED
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("INVALID_STATE_TRANSITION"));

        mockMvc.perform(patch("/api/work-orders/1/status")
                        .with(user("ops").roles("OPERATIONS_USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"IN_PROGRESS\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/work-orders/1/status")
                        .with(user("ops").roles("OPERATIONS_USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"IN_PROGRESS\"}"))         // same status
                .andExpect(status().isConflict());

        mockMvc.perform(patch("/api/work-orders/1/status")
                        .with(user("ops").roles("OPERATIONS_USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"COMPLETED\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/work-orders/1/status")
                        .with(user("ops").roles("OPERATIONS_USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"IN_PROGRESS\"}"))         // back-transition
                .andExpect(status().isConflict());

        assertThat(inventoryRepository.count()).isEqualTo(6); // no stock touched by WO ops
    }

    @Test
    @DisplayName("work orders cannot be assigned to non-operations users")
    void assignmentRoleRuleEnforced() throws Exception {
        mockMvc.perform(post("/api/work-orders")
                        .with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"itemId\":1,\"locationId\":1,\"assignedUserId\":3,\"requiredQuantity\":10}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("BUSINESS_RULE_VIOLATION"));
    }

    @Test
    @DisplayName("unknown status value yields structured validation error")
    void unknownStatusRejected() throws Exception {
        mockMvc.perform(patch("/api/work-orders/1/status")
                        .with(user("ops").roles("OPERATIONS_USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"NOT_A_STATUS\"}"))
                .andExpect(status().isUnprocessableEntity());
    }
}
