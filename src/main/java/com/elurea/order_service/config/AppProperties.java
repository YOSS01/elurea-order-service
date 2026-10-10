package com.elurea.order_service.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;
import java.util.List;

/**
 * Typed view of the "app.*" settings in application.properties.
 */
@ConfigurationProperties("app")
public record AppProperties(
        @DefaultValue Jwt jwt,
        @DefaultValue Cors cors,
        @DefaultValue Services services,
        @DefaultValue Orders orders
) {

    /** Must match the user service, which signs the tokens. */
    public record Jwt(String secret, @DefaultValue("elurea-user-service") String issuer) {
    }

    public record Cors(@DefaultValue("http://localhost:3000") List<String> allowedOrigins) {
    }

    /** Unpaid online orders are cancelled after this delay, so their reserved stock goes back on sale. */
    public record Orders(@DefaultValue("30m") Duration unpaidOnlineTtl) {
    }

    public record Services(
            @DefaultValue("http://localhost:8081") String userUrl,
            @DefaultValue("http://localhost:8082") String productUrl,
            @DefaultValue("http://localhost:8084") String paymentUrl,
            String internalApiKey,
            @DefaultValue("2s") Duration connectTimeout,
            @DefaultValue("5s") Duration readTimeout
    ) {
    }
}
