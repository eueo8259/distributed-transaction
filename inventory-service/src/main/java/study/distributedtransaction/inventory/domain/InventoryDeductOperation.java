package study.distributedtransaction.inventory.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

import java.time.Instant;

@Entity
public class InventoryDeductOperation {

    @Id
    private String idempotencyKey;

    private Long orderId;
    private String sku;
    private int quantity;
    private Instant deductedAt;

    protected InventoryDeductOperation() {
    }

    public InventoryDeductOperation(String idempotencyKey, Long orderId, String sku, int quantity) {
        this.idempotencyKey = idempotencyKey;
        this.orderId = orderId;
        this.sku = sku;
        this.quantity = quantity;
        this.deductedAt = Instant.now();
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
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

    public Instant getDeductedAt() {
        return deductedAt;
    }
}
