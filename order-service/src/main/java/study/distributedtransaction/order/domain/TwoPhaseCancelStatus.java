package study.distributedtransaction.order.domain;

public enum TwoPhaseCancelStatus {
    STARTED,
    PREPARED,
    COMMITTED,
    ROLLED_BACK,
    FAILED
}
