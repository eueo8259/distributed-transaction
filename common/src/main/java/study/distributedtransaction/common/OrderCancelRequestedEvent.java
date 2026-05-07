package study.distributedtransaction.common;

import java.math.BigDecimal;

/**
 * Saga Choreography의 시작 이벤트다.
 * order-service가 자신의 로컬 트랜잭션을 끝낸 뒤 발행한다.
 */
public record OrderCancelRequestedEvent(
        String sagaId,
        Long orderId,
        String sku,
        int quantity,
        String paymentId,
        BigDecimal amount,
        String failurePoint
) {
}
