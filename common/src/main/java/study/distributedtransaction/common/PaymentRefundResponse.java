package study.distributedtransaction.common;

import java.math.BigDecimal;

public record PaymentRefundResponse(
        Long orderId,
        String paymentId,
        BigDecimal refundedAmount,
        String status
) {
}
