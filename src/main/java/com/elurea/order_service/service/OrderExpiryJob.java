package com.elurea.order_service.service;

import com.elurea.order_service.config.AppProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Cancels online orders whose payment never arrived, so their reserved stock goes back on sale.
 * Each order is handled in its own transaction: one failure (e.g. product service down) does not block
 * the others, and the order is simply retried on the next run.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderExpiryJob {

    private static final int BATCH_SIZE = 100;

    private final OrderService orderService;
    private final AppProperties properties;

    @Scheduled(fixedDelayString = "${app.orders.expiry-check-interval:1m}", initialDelayString = "30s")
    public void cancelAbandonedOnlineOrders() {
        Instant cutoff = Instant.now().minus(properties.orders().unpaidOnlineTtl());
        List<UUID> expired = orderService.findExpiredUnpaidOrders(cutoff, BATCH_SIZE);
        for (UUID orderId : expired) {
            try {
                if (orderService.expireIfStillUnpaid(orderId, cutoff)) {
                    log.info("Cancelled unpaid online order {} after {}", orderId, properties.orders().unpaidOnlineTtl());
                }
            } catch (RuntimeException ex) {
                log.warn("Could not expire unpaid order {}, will retry on next run: {}", orderId, ex.getMessage());
            }
        }
    }
}
