package com.elurea.order_service.client;

import com.elurea.order_service.config.AppProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.UUID;

/**
 * Asks the payment service to refund an order's payment, authenticated with the internal API key.
 * The endpoint is idempotent: refunding an already refunded order succeeds without charging back twice.
 */
@Component
public class PaymentServiceClient {

    private static final String SERVICE = "Payment service";
    private static final String API_KEY_HEADER = "X-Internal-Api-Key";

    private final RestClient restClient;
    private final String apiKey;

    public PaymentServiceClient(AppProperties properties) {
        this.restClient = RestClients.create(properties.services().paymentUrl(), properties.services());
        this.apiKey = properties.services().internalApiKey();
    }

    public void refundOrder(UUID orderId) {
        try {
            restClient.post()
                    .uri("/internal/payments/orders/{orderId}/refund", orderId)
                    .header(API_KEY_HEADER, apiKey)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException ex) {
            throw RestClients.unavailable(SERVICE, ex);
        }
    }
}
