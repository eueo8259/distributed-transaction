package study.distributedtransaction.inventory.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

import java.time.Instant;

/**
 * 같은 보상 이벤트가 여러 번 들어와도 중복 보상이 일어나지 않도록 기록을 남긴다.
 */
@Entity
public class InventoryCompensationOperation {

    @Id
    private String sagaId;

    private Long orderId;
    private String sku;
    private int quantity;
    private Instant compensatedAt;

    protected InventoryCompensationOperation() {
    }

    public InventoryCompensationOperation(String sagaId, Long orderId, String sku, int quantity) {
        this.sagaId = sagaId;
        this.orderId = orderId;
        this.sku = sku;
        this.quantity = quantity;
        this.compensatedAt = Instant.now();
    }

    public String getSagaId() {
        return sagaId;
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

    public Instant getCompensatedAt() {
        return compensatedAt;
    }
}
