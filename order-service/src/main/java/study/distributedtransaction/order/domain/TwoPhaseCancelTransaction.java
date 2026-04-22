package study.distributedtransaction.order.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;

import java.time.Instant;

@Entity
public class TwoPhaseCancelTransaction {

    @Id
    private String transactionId;

    private Long orderId;
    private boolean inventoryPrepared;
    private boolean paymentPrepared;
    private boolean inventoryCommitted;
    private boolean paymentCommitted;

    @Enumerated(EnumType.STRING)
    private TwoPhaseCancelStatus status;

    private String failureReason;
    private Instant createdAt;
    private Instant updatedAt;

    protected TwoPhaseCancelTransaction() {
    }

    public TwoPhaseCancelTransaction(String transactionId, Long orderId) {
        this.transactionId = transactionId;
        this.orderId = orderId;
        this.status = TwoPhaseCancelStatus.STARTED;
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    public void markInventoryPrepared() {
        this.inventoryPrepared = true;
        touch();
    }

    public void markPaymentPrepared() {
        this.paymentPrepared = true;
        touch();
    }

    public void markPrepared() {
        this.status = TwoPhaseCancelStatus.PREPARED;
        touch();
    }

    public void markInventoryCommitted() {
        this.inventoryCommitted = true;
        touch();
    }

    public void markPaymentCommitted() {
        this.paymentCommitted = true;
        touch();
    }

    public void markCommitted() {
        this.status = TwoPhaseCancelStatus.COMMITTED;
        touch();
    }

    public void markRolledBack(String reason) {
        this.status = TwoPhaseCancelStatus.ROLLED_BACK;
        this.failureReason = reason;
        touch();
    }

    public void markFailed(String reason) {
        this.status = TwoPhaseCancelStatus.FAILED;
        this.failureReason = reason;
        touch();
    }

    private void touch() {
        this.updatedAt = Instant.now();
    }

    public String getTransactionId() {
        return transactionId;
    }

    public Long getOrderId() {
        return orderId;
    }

    public boolean isInventoryPrepared() {
        return inventoryPrepared;
    }

    public boolean isPaymentPrepared() {
        return paymentPrepared;
    }

    public boolean isInventoryCommitted() {
        return inventoryCommitted;
    }

    public boolean isPaymentCommitted() {
        return paymentCommitted;
    }

    public TwoPhaseCancelStatus getStatus() {
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
