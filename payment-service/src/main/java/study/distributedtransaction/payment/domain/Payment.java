package study.distributedtransaction.payment.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;

import java.math.BigDecimal;

@Entity
public class Payment {

    @Id
    private String paymentId;

    private Long orderId;
    private BigDecimal amount;
    private BigDecimal refundedAmount;

    @Enumerated(EnumType.STRING)
    private PaymentStatus status;

    protected Payment() {
    }

    public Payment(String paymentId, Long orderId, BigDecimal amount) {
        this.paymentId = paymentId;
        this.orderId = orderId;
        this.amount = amount;
        this.refundedAmount = BigDecimal.ZERO;
        this.status = PaymentStatus.PAID;
    }

    public void refund(BigDecimal refundAmount) {
        if (status == PaymentStatus.REFUNDED) {
            return;
        }
        if (amount.compareTo(refundAmount) != 0) {
            throw new IllegalArgumentException("Only full refund is supported in this skeleton.");
        }
        this.refundedAmount = refundAmount;
        this.status = PaymentStatus.REFUNDED;
    }

    public String getPaymentId() {
        return paymentId;
    }

    public Long getOrderId() {
        return orderId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public BigDecimal getRefundedAmount() {
        return refundedAmount;
    }

    public PaymentStatus getStatus() {
        return status;
    }
}
