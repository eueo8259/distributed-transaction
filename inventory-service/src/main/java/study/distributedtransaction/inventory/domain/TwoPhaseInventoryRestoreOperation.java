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

    // PREPARED: coordinator가 작업 가능 여부를 확인한 시각.
    private Instant preparedAt;
    // PRE_COMMITTED: coordinator가 commit 방향 결정을 알린 시각. timeout 자율 commit의 기준이 된다.
    private Instant preCommittedAt;
    // COMMITTED 또는 ROLLED_BACK으로 최종 결정된 시각.
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

    public void preCommit() {
        this.status = TwoPhaseInventoryStatus.PRE_COMMITTED;
        this.preCommittedAt = Instant.now();
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

    public Instant getPreCommittedAt() {
        return preCommittedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }
}
