package study.distributedtransaction.common;

public record InventoryDeductRequest(
        Long orderId,
        String sku,
        int quantity,
        String idempotencyKey
) {
}
