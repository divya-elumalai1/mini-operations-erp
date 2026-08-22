package com.minierp.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** Logs real users in through the real /api/auth/login endpoint and extracts JWTs. */
@Component
public class TestTokens {

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;

    public TestTokens(MockMvc mockMvc, ObjectMapper objectMapper) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
    }

    public String admin() {
        return obtain("admin", "Admin@123");
    }

    public String ops() {
        return obtain("ops", "Ops@123");
    }

    public String sales() {
        return obtain("sales", "Sales@123");
    }

    private String obtain(String username, String password) {
        try {
            MvcResult result = mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"%s\",\"password\":\"%s\"}".formatted(username, password)))
                    .andReturn();
            if (result.getResponse().getStatus() != 200) {
                throw new IllegalStateException("Login failed for " + username + ": "
                        + result.getResponse().getContentAsString());
            }
            JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
            return body.get("token").asText();
        } catch (Exception e) {
            throw new IllegalStateException("Could not obtain token for " + username, e);
        }
    }
}
