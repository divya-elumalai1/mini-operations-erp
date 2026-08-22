package com.minierp.it;

import com.minierp.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Error contract: one consistent envelope, no stack traces, correct codes.
 */
class ValidationErrorIT extends AbstractIntegrationTest {

    @Test
    @DisplayName("bean validation failures return 400 with field details array")
    void validationErrorsHaveFieldDetails() throws Exception {
        mockMvc.perform(post("/api/orders")
                        .with(user("sales").roles("SALES_USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerName\":\"\",\"lines\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.path").value("/api/orders"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.details").isArray());
    }

    @Test
    @DisplayName("negative quantity on receipt returns structured 400")
    void negativeQuantityRejected() throws Exception {
        mockMvc.perform(post("/api/inventory/receipts")
                        .with(user("ops").roles("OPERATIONS_USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"itemId\":1,\"locationId\":1,\"quantity\":-5}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details[0].field").value("quantity"));
    }

    @Test
    @DisplayName("unknown resource returns 404 envelope")
    void unknownResourceIs404() throws Exception {
        mockMvc.perform(get("/api/work-orders/9999")
                        .with(user("sales").roles("SALES_USER")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));
    }

    @Test
    @DisplayName("malformed JSON returns 400, not a stack trace")
    void malformedJsonRejected() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not json"))
                .andExpect(status().isBadRequest());
    }
}
