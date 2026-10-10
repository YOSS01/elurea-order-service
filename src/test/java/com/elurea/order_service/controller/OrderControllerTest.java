package com.elurea.order_service.controller;

import com.elurea.order_service.config.AppProperties;
import com.elurea.order_service.security.SecurityConfig;
import com.elurea.order_service.security.SecurityProblemHandler;
import com.elurea.order_service.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.UUID;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrderController.class)
@Import({SecurityConfig.class, SecurityProblemHandler.class, OrderControllerTest.Config.class})
class OrderControllerTest {

    @TestConfiguration
    @EnableConfigurationProperties(AppProperties.class)
    static class Config {
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderService orderService;

    private static RequestPostProcessor customer() {
        return jwt().jwt(token -> token.subject(UUID.randomUUID().toString()))
                .authorities(new SimpleGrantedAuthority("ROLE_USER"));
    }

    @Test
    void ordersRequireAToken() throws Exception {
        mockMvc.perform(get("/api/orders"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void checkoutValidatesTheRequest() throws Exception {
        String body = """
                {"phoneNumber": "abc", "paymentMethod": "BITCOIN_MAYBE", "items": []}
                """;

        mockMvc.perform(post("/api/orders").with(customer()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(orderService);
    }

    @Test
    void checkoutReportsEveryInvalidField() throws Exception {
        String body = """
                {"phoneNumber": "abc", "paymentMethod": "ONLINE",
                 "items": [{"variantId": "6f1c1a52-6a39-4c3b-9d0e-1f2a3b4c5d6e", "quantity": 0}]}
                """;

        mockMvc.perform(post("/api/orders").with(customer()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.shippingAddressId").exists())
                .andExpect(jsonPath("$.errors.phoneNumber").exists())
                .andExpect(jsonPath("$.errors['items[0].quantity']").exists());
        verifyNoInteractions(orderService);
    }

    @Test
    void customersCannotChangeOrderStatus() throws Exception {
        mockMvc.perform(patch("/api/orders/{id}/status", UUID.randomUUID()).with(customer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\": \"DELIVERED\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(orderService);
    }

    @Test
    void unknownSortFieldIsRejected() throws Exception {
        mockMvc.perform(get("/api/orders").with(customer()).param("sort", "customerEmail"))
                .andExpect(status().isBadRequest());
    }
}
