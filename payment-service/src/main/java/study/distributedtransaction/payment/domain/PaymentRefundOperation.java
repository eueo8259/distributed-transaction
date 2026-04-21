package study.distributedtransaction.payment.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
public class PaymentRefundOperation {

    @Id
    private String idempotencyKey;

    private Long orderId;
    private String paymentId;
    private BigDecimal amount;
    private Instant refundedAt;

    protected PaymentRefundOperation() {
    }

    public PaymentRefundOperation(String idempotencyKey, Long orderId, String paymentId, BigDecimal amount) {
        this.idempotencyKey = idempotencyKey;
        this.orderId = orderId;
        this.paymentId = paymentId;
        this.amount = amount;
        this.refundedAt = Instant.now();
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
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

    public Instant getRefundedAt() {
        return refundedAt;
    }
}
