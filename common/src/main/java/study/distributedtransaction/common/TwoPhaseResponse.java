package study.distributedtransaction.common;

public record TwoPhaseResponse(
        String transactionId,
        String participant,
        String status,
        String message
) {
}
