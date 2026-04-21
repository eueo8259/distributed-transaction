package study.distributedtransaction.order.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;

@Entity
@Table(name = "orders")
public class PurchaseOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String sku;
    private int quantity;
    private String paymentId;
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    private OrderStatus status;

    protected PurchaseOrder() {
    }

    public PurchaseOrder(String sku, int quantity, String paymentId, BigDecimal amount) {
        this.sku = sku;
        this.quantity = quantity;
        this.paymentId = paymentId;
        this.amount = amount;
        this.status = OrderStatus.PAID;
    }

    public void requestCancel() {
        if (status != OrderStatus.PAID) {
            throw new IllegalStateException("Only PAID orders can be cancelled. currentStatus=" + status);
        }
        this.status = OrderStatus.CANCEL_REQUESTED;
    }

    public void completeCancel() {
        this.status = OrderStatus.CANCELLED;
    }

    public void failCancel() {
        this.status = OrderStatus.CANCEL_FAILED;
    }

    public Long getId() {
        return id;
    }

    public String getSku() {
        return sku;
    }

    public int getQuantity() {
        return quantity;
    }

    public String getPaymentId() {
        return paymentId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public OrderStatus getStatus() {
        return status;
    }
}
