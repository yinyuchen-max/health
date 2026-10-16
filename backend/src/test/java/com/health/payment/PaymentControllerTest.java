package com.health.payment;

import com.health.common.config.GlobalExceptionHandler;
import com.health.common.utils.SecurityUtil;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.Map;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PaymentControllerTest {
    @Test void mixedCaseWechatHeadersAreNormalizedAndRawBodyIsPreserved() throws Exception {
        var service=mock(PaymentService.class);
        var mvc=MockMvcBuilders.standaloneSetup(new PaymentController(service,mock(SecurityUtil.class))).build();
        String body="{ \"resource\" : \"keep whitespace for signature\" }";
        mvc.perform(post("/api/ai/payments/notify/wechat").contentType("application/json").content(body)
                        .header("Wechatpay-Timestamp","123").header("Wechatpay-Signature","test-signature")
                        .header("Wechatpay-Nonce","test-nonce").header("Wechatpay-Serial","PUB_KEY_ID_TEST"))
                .andExpect(status().isNoContent());
        verify(service).notification(eq("wechat"),eq(body),argThat(headers -> "123".equals(headers.get("wechatpay-timestamp"))
                && "test-signature".equals(headers.get("wechatpay-signature"))),eq(Map.of()));
    }
    @Test void createUsesAuthenticatedIdentityAndDoesNotAcceptClientAmountOrPaidStatus() throws Exception {
        var service=mock(PaymentService.class); var security=mock(SecurityUtil.class);
        when(security.getCurrentUserId()).thenReturn(1L);
        var mvc=MockMvcBuilders.standaloneSetup(new PaymentController(service,security))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(post("/api/ai/payments/orders").contentType("application/json").content("""
                {"plan":"plus","channel":"wechat","requestId":"00000000-0000-0000-0000-000000000001",
                 "userId":2,"amountCents":1,"status":"PAID"}
                """)).andExpect(status().isOk());
        verify(service).create(1L,"plus","wechat","00000000-0000-0000-0000-000000000001");
    }
    @Test void failedNotificationDoesNotAcknowledgeSuccess() throws Exception {
        var service=mock(PaymentService.class);
        doThrow(new IllegalArgumentException("invalid signature")).when(service).notification(anyString(),anyString(),anyMap(),anyMap());
        var mvc=MockMvcBuilders.standaloneSetup(new PaymentController(service,mock(SecurityUtil.class))).build();
        mvc.perform(post("/api/ai/payments/notify/wechat").contentType("application/json").content("{}"))
                .andExpect(status().is5xxServerError());
        mvc.perform(post("/api/ai/payments/notify/alipay").contentType("application/x-www-form-urlencoded").param("sign","forged"))
                .andExpect(status().is5xxServerError()).andExpect(content().string("failure"));
    }
}
