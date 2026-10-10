package com.elurea.order_service.entity;

import com.elurea.order_service.enums.Status;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StatusTest {

    @Test
    void happyPathIsAllowedStepByStep() {
        assertThat(Status.PENDING.canTransitionTo(Status.CONFIRMED)).isTrue();
        assertThat(Status.CONFIRMED.canTransitionTo(Status.PACKAGING)).isTrue();
        assertThat(Status.PACKAGING.canTransitionTo(Status.IN_TRANSIT)).isTrue();
        assertThat(Status.IN_TRANSIT.canTransitionTo(Status.DELIVERED)).isTrue();
        assertThat(Status.DELIVERED.canTransitionTo(Status.RETURNED)).isTrue();
    }

    @Test
    void stepsCannotBeSkippedOrReversed() {
        assertThat(Status.PENDING.canTransitionTo(Status.DELIVERED)).isFalse();
        assertThat(Status.DELIVERED.canTransitionTo(Status.PENDING)).isFalse();
        assertThat(Status.PENDING.canTransitionTo(Status.PENDING)).isFalse();
    }

    @Test
    void cancellationOnlyBeforeShipping() {
        assertThat(Status.PACKAGING.canTransitionTo(Status.CANCELLED)).isTrue();
        assertThat(Status.IN_TRANSIT.canTransitionTo(Status.CANCELLED)).isFalse();
        assertThat(Status.DELIVERED.canTransitionTo(Status.CANCELLED)).isFalse();
    }

    @Test
    void finalStatusesHaveNoNextStep() {
        assertThat(Status.CANCELLED.allowedNext()).isEmpty();
        assertThat(Status.RETURNED.allowedNext()).isEmpty();
    }

    @Test
    void customersCanCancelOnlyBeforePreparation() {
        assertThat(Status.PENDING.isCancellableByCustomer()).isTrue();
        assertThat(Status.CONFIRMED.isCancellableByCustomer()).isTrue();
        assertThat(Status.PACKAGING.isCancellableByCustomer()).isFalse();
    }
}
