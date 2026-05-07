package study.distributedtransaction.common;

import java.math.BigDecimal;

/**
 * 재고 복구가 이미 끝난 뒤 환불이 실패했을 때 발행하는 이벤트다.
 * inventory-service는 이 이벤트를 받아 보상 트랜잭션을 수행한다.
 */
public record PaymentRefundFailedEvent(
        String sagaId,
        Long orderId,
        String sku,
        int quantity,
        String paymentId,
        BigDecimal amount,
        String reason,
        String failurePoint
) {
}
