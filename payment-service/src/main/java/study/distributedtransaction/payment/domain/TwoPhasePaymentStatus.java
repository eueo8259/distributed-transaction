package study.distributedtransaction.payment.domain;

public enum TwoPhasePaymentStatus {
    PREPARED,
    PRE_COMMITTED,
    COMMITTED,
    ROLLED_BACK
}
