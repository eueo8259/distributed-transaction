package study.distributedtransaction.order.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;

import java.time.Instant;

@Entity
public class CancelOrderSaga {

    @Id
    private String sagaId;

    private Long orderId;
    private boolean inventoryRestored;
    private boolean paymentRefunded;
    private boolean inventoryCompensated;

    @Enumerated(EnumType.STRING)
    private CancelOrderSagaStatus status;

    private String failureReason;
    private Instant createdAt;
    private Instant updatedAt;

    protected CancelOrderSaga() {
    }

    public CancelOrderSaga(String sagaId, Long orderId) {
        this.sagaId = sagaId;
        this.orderId = orderId;
        this.status = CancelOrderSagaStatus.STARTED;
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    public void markInventoryRestored() {
        this.inventoryRestored = true;
        this.status = CancelOrderSagaStatus.INVENTORY_RESTORED;
        touch();
    }

    public void markPaymentRefunded() {
        this.paymentRefunded = true;
        this.status = CancelOrderSagaStatus.PAYMENT_REFUNDED;
        touch();
    }

    public void markInventoryCompensated(String reason) {
        this.inventoryCompensated = true;
        this.status = CancelOrderSagaStatus.INVENTORY_COMPENSATED;
        this.failureReason = reason;
        touch();
    }

    public void markCompleted() {
        this.status = CancelOrderSagaStatus.COMPLETED;
        touch();
    }

    public void markFailed(String reason) {
        this.status = CancelOrderSagaStatus.FAILED;
        this.failureReason = reason;
        touch();
    }

    private void touch() {
        this.updatedAt = Instant.now();
    }

    public String getSagaId() {
        return sagaId;
    }

    public Long getOrderId() {
        return orderId;
    }

    public boolean isInventoryRestored() {
        return inventoryRestored;
    }

    public boolean isPaymentRefunded() {
        return paymentRefunded;
    }

    public boolean isInventoryCompensated() {
        return inventoryCompensated;
    }

    public CancelOrderSagaStatus getStatus() {
        return status;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
