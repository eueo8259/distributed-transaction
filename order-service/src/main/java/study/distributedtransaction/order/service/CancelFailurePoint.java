package study.distributedtransaction.order.service;

public enum CancelFailurePoint {
    NONE,
    INVENTORY_RESTORE,
    PAYMENT_REFUND,
    INVENTORY_COMPENSATE;

    public boolean matches(String failurePoint) {
        return name().equalsIgnoreCase(failurePoint);
    }
}
