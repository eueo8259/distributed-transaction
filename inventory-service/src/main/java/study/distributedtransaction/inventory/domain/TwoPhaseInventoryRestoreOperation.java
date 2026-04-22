package study.distributedtransaction.inventory.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;

import java.time.Instant;

@Entity
public class TwoPhaseInventoryRestoreOperation {

    @Id
    private String transactionId;

    private Long orderId;
    private String sku;
    private int quantity;

    @Enumerated(EnumType.STRING)
    private TwoPhaseInventoryStatus status;

    private Instant preparedAt;
    private Instant completedAt;

    protected TwoPhaseInventoryRestoreOperation() {
    }

    public TwoPhaseInventoryRestoreOperation(String transactionId, Long orderId, String sku, int quantity) {
        this.transactionId = transactionId;
        this.orderId = orderId;
        this.sku = sku;
        this.quantity = quantity;
        this.status = TwoPhaseInventoryStatus.PREPARED;
        this.preparedAt = Instant.now();
    }

    public void commit() {
        this.status = TwoPhaseInventoryStatus.COMMITTED;
        this.completedAt = Instant.now();
    }

    public void rollback() {
        this.status = TwoPhaseInventoryStatus.ROLLED_BACK;
        this.completedAt = Instant.now();
    }

    public String getTransactionId() {
        return transactionId;
    }

    public Long getOrderId() {
        return orderId;
    }

    public String getSku() {
        return sku;
    }

    public int getQuantity() {
        return quantity;
    }

    public TwoPhaseInventoryStatus getStatus() {
        return status;
    }

    public Instant getPreparedAt() {
        return preparedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }
}
