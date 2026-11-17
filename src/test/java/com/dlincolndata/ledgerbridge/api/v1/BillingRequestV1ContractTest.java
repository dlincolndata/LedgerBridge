package com.dlincolndata.ledgerbridge.api.v1;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import java.util.UUID;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(BillingRequestV1Controller.class)
@Import(BillingIdempotencyService.class)
class BillingRequestV1ContractTest {
    @Autowired private MockMvc mvc;

    @Test
    void rejectsAbsentCurrency() throws Exception {
        String cid = UUID.randomUUID().toString();
        String body = "{\"identity\":{\"provider\":\"salesforce\",\"business\":\"1710\",\"entityType\":\"billing_request\",\"sourceId\":\"ORD-1\"},\"revision\":1,\"lines\":[{\"lineNumber\":1,\"sku\":\"SUB\",\"quantity\":2,\"unitPrice\":100.00}]}";
        mvc.perform(post("/api/v1/billing-requests").header("X-Correlation-ID", cid).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value("LB_VALIDATION_MISSING_CURRENCY"));
    }

    @Test
    void rejectsInvalidQuantity() throws Exception {
        String cid = UUID.randomUUID().toString();
        String body = "{\"identity\":{\"provider\":\"salesforce\",\"business\":\"1710\",\"entityType\":\"billing_request\",\"sourceId\":\"ORD-2\"},\"revision\":1,\"currency\":\"USD\",\"lines\":[{\"lineNumber\":1,\"sku\":\"SUB\",\"quantity\":0,\"unitPrice\":100.00}]}";
        mvc.perform(post("/api/v1/billing-requests").header("X-Correlation-ID", cid).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value("LB_VALIDATION_INVALID_QUANTITY"));
    }

    @Test
    void rejectsReusedKeyWithAlteredPayload() throws Exception {
        String cid = UUID.randomUUID().toString();
        String p1 = "{\"identity\":{\"provider\":\"Salesforce\",\"business\":\"1710\",\"entityType\":\"billing_request\",\"sourceId\":\"ORD-3\"},\"revision\":1,\"currency\":\"USD\",\"lines\":[{\"lineNumber\":1,\"sku\":\"SUB\",\"quantity\":2,\"unitPrice\":100.00}]}";
        String p2 = "{\"identity\":{\"provider\":\"Salesforce\",\"business\":\"1710\",\"entityType\":\"billing_request\",\"sourceId\":\"ORD-3\"},\"revision\":1,\"currency\":\"USD\",\"lines\":[{\"lineNumber\":1,\"sku\":\"SUB\",\"quantity\":5,\"unitPrice\":100.00}]}";
        mvc.perform(post("/api/v1/billing-requests").header("X-Correlation-ID", cid).contentType(MediaType.APPLICATION_JSON).content(p1))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.idempotencyKey").value("salesforce:1710:billing_request:ORD-3:rev-1"));
        mvc.perform(post("/api/v1/billing-requests").header("X-Correlation-ID", cid).contentType(MediaType.APPLICATION_JSON).content(p1))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/billing-requests").header("X-Correlation-ID", cid).contentType(MediaType.APPLICATION_JSON).content(p2))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.errorCode").value("LB_IDEMPOTENCY_CONFLICT"));
    }
}
