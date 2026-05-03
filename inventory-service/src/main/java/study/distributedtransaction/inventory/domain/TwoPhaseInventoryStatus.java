package study.distributedtransaction.inventory.domain;

public enum TwoPhaseInventoryStatus {
    PREPARED,
    PRE_COMMITTED,
    COMMITTED,
    ROLLED_BACK
}
