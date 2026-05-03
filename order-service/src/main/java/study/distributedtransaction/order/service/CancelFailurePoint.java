package study.distributedtransaction.order.service;

public enum CancelFailurePoint {
    NONE,
    INVENTORY,
    PAYMENT,
    AFTER_INVENTORY,
    INVENTORY_PREPARE,
    PAYMENT_PREPARE,
    AFTER_PREPARE,
    INVENTORY_PRE_COMMIT,
    PAYMENT_PRE_COMMIT,
    AFTER_PRE_COMMIT,
    INVENTORY_COMMIT,
    PAYMENT_COMMIT
}
