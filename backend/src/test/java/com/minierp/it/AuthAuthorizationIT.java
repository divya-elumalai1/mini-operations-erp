package com.minierp.it;

import com.minierp.support.AbstractIntegrationTest;
import com.minierp.support.TestTokens;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Assignment Test 5 territory: backend authentication + authorization.
 * Proves 401 for unauthenticated calls, 401 for bad credentials,
 * and role enforcement on every restricted operation.
 */
class AuthAuthorizationIT extends AbstractIntegrationTest {

    @Autowired
    private TestTokens tokens;

    private String adminToken;
    private String opsToken;
    private String salesToken;

    @BeforeEach
    void obtainTokens() {
        adminToken = tokens.admin();
        opsToken = tokens.ops();
        salesToken = tokens.sales();
    }

    @Test
    @DisplayName("login with valid credentials returns token and role")
    void loginSuccess() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"sales\",\"password\":\"Sales@123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.role").value("SALES_USER"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"));
    }

    @Test
    @DisplayName("login with wrong password is rejected with 401 and vague message")
    void loginWrongPassword() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"nope\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("AUTHENTICATION_ERROR"));
    }

    @Test
    @DisplayName("protected endpoint without token returns 401 envelope")
    void noTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/inventory"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    @DisplayName("garbage token returns 401")
    void garbageTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/inventory").header("Authorization", "Bearer not.a.jwt"))
                .andExpect(status().isUnauthorized());
    }

    // ---------- Mandatory Test 5: unauthorized user gets 403 ----------

    @Test
    @DisplayName("SALES_USER cannot goods-receipt inventory (403)")
    void salesCannotReceiptInventory() throws Exception {
        mockMvc.perform(post("/api/inventory/receipts")
                        .header("Authorization", bearer(salesToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(receiptBody()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("AUTHORIZATION_ERROR"));
    }

    @Test
    @DisplayName("OPERATIONS_USER cannot create work orders (403)")
    void opsCannotCreateWorkOrder() throws Exception {
        mockMvc.perform(post("/api/work-orders")
                        .header("Authorization", bearer(opsToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"itemId":4,"locationId":2,"assignedUserId":2,"requiredQuantity":5}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("SALES_USER cannot create work orders (403)")
    void salesCannotCreateWorkOrder() throws Exception {
        mockMvc.perform(post("/api/work-orders")
                        .header("Authorization", bearer(salesToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"itemId":4,"locationId":2,"assignedUserId":2,"requiredQuantity":5}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("OPERATIONS_USER cannot create customer orders (403)")
    void opsCannotCreateCustomerOrder() throws Exception {
        mockMvc.perform(post("/api/orders")
                        .header("Authorization", bearer(opsToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderBody()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("SALES_USER cannot create transfers (403)")
    void salesCannotCreateTransfer() throws Exception {
        mockMvc.perform(post("/api/transfers")
                        .header("Authorization", bearer(salesToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sourceLocationId":1,"destinationLocationId":2,"itemId":1,"quantity":10}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("SALES_USER cannot dispatch or receive transfers (403)")
    void salesCannotMoveTransfers() throws Exception {
        mockMvc.perform(post("/api/transfers/1/dispatch").header("Authorization", bearer(salesToken)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/transfers/1/receive").header("Authorization", bearer(salesToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("ADMIN can do everything: receipt, transfer lifecycle, work orders")
    void adminHasFullAccess() throws Exception {
        mockMvc.perform(post("/api/inventory/receipts")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(receiptBody()))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/transfers/1/dispatch").header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/work-orders/1/status")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"IN_PROGRESS\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("reads are allowed for every authenticated role")
    void readsAllowedForAllRoles() throws Exception {
        mockMvc.perform(get("/api/inventory").header("Authorization", bearer(salesToken)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/work-orders").header("Authorization", bearer(opsToken)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/orders").header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk());
    }

    private String receiptBody() {
        return "{\"itemId\":1,\"locationId\":1,\"quantity\":25,\"idempotencyKey\":\"auth-it-%s\"}"
                .formatted(System.nanoTime());
    }

    private String orderBody() {
        return "{\"customerName\":\"Acme\",\"lines\":[{\"itemId\":3,\"locationId\":1,\"quantity\":5}]}";
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
