package study.distributedtransaction.inventory.domain;

public enum TwoPhaseInventoryStatus {
    PREPARED,
    COMMITTED,
    ROLLED_BACK
}
