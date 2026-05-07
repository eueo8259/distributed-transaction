package study.distributedtransaction.common;

import java.math.BigDecimal;

/**
 * payment-service가 환불 로컬 트랜잭션을 성공적으로 끝낸 뒤 발행하는 이벤트다.
 * order-service는 이 이벤트를 받아 주문 취소 Saga를 최종 완료한다.
 */
public record PaymentRefundedEvent(
        String sagaId,
        Long orderId,
        String paymentId,
        BigDecimal amount
) {
}
