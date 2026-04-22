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

    private Instant preparedAt;
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

    public Instant getCompletedAt() {
        return completedAt;
    }
}
