package study.distributedtransaction.common;

/**
 * inventory-service의 로컬 트랜잭션이 실패해 Saga가 다음 단계로 가지 못할 때 발행하는 이벤트다.
 * order-service는 이 이벤트를 받아 주문 취소 실패 상태로 마킹한다.
 */
public record InventoryRestoreFailedEvent(
        String sagaId,
        Long orderId,
        String reason
) {
}
