package study.distributedtransaction.common;

public record InventoryDeductResponse(
        Long orderId,
        String sku,
        int deductedQuantity,
        int currentStock,
        String status
) {
}
