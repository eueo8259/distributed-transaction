package study.distributedtransaction.order.domain;

public enum TwoPhaseCancelStatus {
    STARTED,
    PREPARED,
    PRE_COMMITTED,
    COMMITTED,
    ROLLED_BACK,
    FAILED
}
