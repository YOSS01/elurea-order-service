package com.elurea.order_service.client;

import com.elurea.order_service.config.AppProperties;
import com.elurea.order_service.exception.BadRequestException;
import com.elurea.order_service.exception.ResourceNotFoundException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.UUID;

/**
 * Reads the customer's profile and addresses from the user service, on behalf of the customer:
 * their own token is forwarded, so the user service applies its ownership rules.
 */
@Component
public class UserServiceClient {

    private static final String SERVICE = "User service";

    private final RestClient restClient;

    public UserServiceClient(AppProperties properties) {
        this.restClient = RestClients.create(properties.services().userUrl(), properties.services());
    }

    public UserProfile getProfile(String bearerToken) {
        try {
            return restClient.get()
                    .uri("/api/auth/me")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken)
                    .retrieve()
                    .body(UserProfile.class);
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().isSameCodeAs(HttpStatus.NOT_FOUND)) {
                throw new BadRequestException("Your account no longer exists");
            }
            throw RestClients.unavailable(SERVICE, ex);
        } catch (RestClientException ex) {
            throw RestClients.unavailable(SERVICE, ex);
        }
    }

    /** 404 if the address does not exist, 403 if it belongs to someone else. */
    public UserAddress getAddress(UUID addressId, String bearerToken) {
        try {
            return restClient.get()
                    .uri("/api/addresses/{id}", addressId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken)
                    .retrieve()
                    .body(UserAddress.class);
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().isSameCodeAs(HttpStatus.NOT_FOUND)) {
                throw new ResourceNotFoundException("Address", addressId);
            }
            if (ex.getStatusCode().isSameCodeAs(HttpStatus.FORBIDDEN)) {
                throw new AccessDeniedException("This address belongs to another user");
            }
            throw RestClients.unavailable(SERVICE, ex);
        } catch (RestClientException ex) {
            throw RestClients.unavailable(SERVICE, ex);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record UserProfile(UUID id, String title, String name, String email, String phoneNumber) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record UserAddress(UUID id, UUID userId, String addressLine1, String addressLine2,
                              String city, String state, String country, String postalCode) {
    }
}
