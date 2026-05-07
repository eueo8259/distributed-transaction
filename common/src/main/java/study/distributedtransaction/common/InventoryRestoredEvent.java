package study.distributedtransaction.common;

import java.math.BigDecimal;

/**
 * inventory-service가 재고 복구를 성공적으로 끝낸 뒤 발행하는 이벤트다.
 * payment-service는 이 이벤트를 받아 환불 로컬 트랜잭션을 시작한다.
 */
public record InventoryRestoredEvent(
        String sagaId,
        Long orderId,
        String sku,
        int quantity,
        String paymentId,
        BigDecimal amount,
        String failurePoint
) {
}
