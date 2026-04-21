package study.distributedtransaction.common;

public record CancelOrderResponse(
        Long orderId,
        String status,
        boolean inventoryRestored,
        boolean paymentRefunded,
        String message
) {
}
