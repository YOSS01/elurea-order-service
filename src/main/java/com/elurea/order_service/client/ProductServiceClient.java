package com.elurea.order_service.client;

import com.elurea.order_service.config.AppProperties;
import com.elurea.order_service.exception.BadRequestException;
import com.elurea.order_service.exception.ConflictException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Reserves and releases stock in the product service, authenticated with the internal API key.
 * Prices always come from here, never from the client.
 */
@Component
public class ProductServiceClient {

    private static final String SERVICE = "Product service";
    private static final String API_KEY_HEADER = "X-Internal-Api-Key";

    private final RestClient restClient;
    private final String apiKey;

    public ProductServiceClient(AppProperties properties) {
        this.restClient = RestClients.create(properties.services().productUrl(), properties.services());
        this.apiKey = properties.services().internalApiKey();
    }

    /**
     * Takes the stock for every line, all-or-nothing, and returns the current price and product details.
     * 409 when a line is out of stock; 400 when a variant does not exist or was withdrawn.
     */
    public List<ReservedItem> reserve(List<StockLine> lines) {
        try {
            return restClient.post()
                    .uri("/api/product-variants/stock/reserve")
                    .header(API_KEY_HEADER, apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("items", lines))
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<ReservedItem>>() {
                    });
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().isSameCodeAs(HttpStatus.CONFLICT)) {
                throw new ConflictException(RestClients.detailOf(ex));
            }
            if (ex.getStatusCode().isSameCodeAs(HttpStatus.NOT_FOUND)
                    || ex.getStatusCode().isSameCodeAs(HttpStatus.BAD_REQUEST)) {
                throw new BadRequestException(RestClients.detailOf(ex));
            }
            throw RestClients.unavailable(SERVICE, ex);
        } catch (RestClientException ex) {
            throw RestClients.unavailable(SERVICE, ex);
        }
    }

    /** Puts stock back. Any failure is reported, so the caller can roll back and retry later. */
    public void release(List<StockLine> lines) {
        try {
            restClient.post()
                    .uri("/api/product-variants/stock/release")
                    .header(API_KEY_HEADER, apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("items", lines))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException ex) {
            throw RestClients.unavailable(SERVICE, ex);
        }
    }

    public record StockLine(UUID variantId, int quantity) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ReservedItem(UUID variantId, UUID productId, String productTitle, String thumbnail,
                               String sku, String size, BigDecimal unitPrice, int quantity) {
    }
}
