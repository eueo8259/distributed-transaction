package study.distributedtransaction.inventory.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

@Entity
public class InventoryItem {

    @Id
    private String sku;

    private int stock;

    protected InventoryItem() {
    }

    public InventoryItem(String sku, int stock) {
        this.sku = sku;
        this.stock = stock;
    }

    public void restore(int quantity) {
        this.stock += quantity;
    }

    public void deduct(int quantity) {
        if (stock < quantity) {
            throw new IllegalStateException("Not enough stock to compensate. sku=" + sku + ", stock=" + stock);
        }
        this.stock -= quantity;
    }

    public String getSku() {
        return sku;
    }

    public int getStock() {
        return stock;
    }
}
