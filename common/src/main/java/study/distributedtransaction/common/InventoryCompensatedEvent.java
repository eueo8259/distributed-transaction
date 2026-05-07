package study.distributedtransaction.common;

/**
 * inventory-service가 이전 재고 복구를 보상한 뒤 발행하는 이벤트다.
 * order-service는 이 이벤트를 받아 Saga를 실패 상태로 마킹한다.
 */
public record InventoryCompensatedEvent(
        String sagaId,
        Long orderId,
        String sku,
        int quantity
) {
}
