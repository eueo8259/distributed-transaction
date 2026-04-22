package study.distributedtransaction.common;

public record TwoPhaseDecisionRequest(
        String transactionId
) {
}
