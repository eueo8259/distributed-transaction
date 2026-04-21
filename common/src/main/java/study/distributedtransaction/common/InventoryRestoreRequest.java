package study.distributedtransaction.common;

public record InventoryRestoreRequest(
        Long orderId,
        String sku,
        int quantity,
        String idempotencyKey
) {
}
