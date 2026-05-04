package study.distributedtransaction.order.domain;

public enum CancelOrderSagaStatus {
    STARTED,
    INVENTORY_RESTORED,
    PAYMENT_REFUNDED,
    INVENTORY_COMPENSATED,
    COMPLETED,
    FAILED
}
