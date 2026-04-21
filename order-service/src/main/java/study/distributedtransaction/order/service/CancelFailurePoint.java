package study.distributedtransaction.order.service;

public enum CancelFailurePoint {
    NONE,
    INVENTORY,
    PAYMENT,
    AFTER_INVENTORY
}
