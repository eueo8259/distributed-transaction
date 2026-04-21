package study.distributedtransaction.common;

public record InventoryRestoreResponse(
        Long orderId,
        String sku,
        int restoredQuantity,
        int currentStock,
        String status
) {
}
