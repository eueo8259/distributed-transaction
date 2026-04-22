package study.distributedtransaction.payment.domain;

public enum TwoPhasePaymentStatus {
    PREPARED,
    COMMITTED,
    ROLLED_BACK
}
