package study.distributedtransaction.common;

import java.math.BigDecimal;

public record PaymentRefundRequest(
        Long orderId,
        String paymentId,
        BigDecimal amount,
        String idempotencyKey
) {
}
