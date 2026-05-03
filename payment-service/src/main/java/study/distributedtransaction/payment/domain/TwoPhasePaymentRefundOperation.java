package study.distributedtransaction.payment.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
public class TwoPhasePaymentRefundOperation {

    @Id
    private String transactionId;

    private Long orderId;
    private String paymentId;
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    private TwoPhasePaymentStatus status;

    // PREPARED: coordinator가 작업 가능 여부를 확인한 시각.
    private Instant preparedAt;
    // PRE_COMMITTED: coordinator가 commit 방향 결정을 알린 시각. timeout 자율 commit의 기준이 된다.
    private Instant preCommittedAt;
    // COMMITTED 또는 ROLLED_BACK으로 최종 결정된 시각.
    private Instant completedAt;

    protected TwoPhasePaymentRefundOperation() {
    }

    public TwoPhasePaymentRefundOperation(String transactionId, Long orderId, String paymentId, BigDecimal amount) {
        this.transactionId = transactionId;
        this.orderId = orderId;
        this.paymentId = paymentId;
        this.amount = amount;
        this.status = TwoPhasePaymentStatus.PREPARED;
        this.preparedAt = Instant.now();
    }

    public void commit() {
        this.status = TwoPhasePaymentStatus.COMMITTED;
        this.completedAt = Instant.now();
    }

    public void preCommit() {
        this.status = TwoPhasePaymentStatus.PRE_COMMITTED;
        this.preCommittedAt = Instant.now();
    }

    public void rollback() {
        this.status = TwoPhasePaymentStatus.ROLLED_BACK;
        this.completedAt = Instant.now();
    }

    public String getTransactionId() {
        return transactionId;
    }

    public Long getOrderId() {
        return orderId;
    }

    public String getPaymentId() {
        return paymentId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public TwoPhasePaymentStatus getStatus() {
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
