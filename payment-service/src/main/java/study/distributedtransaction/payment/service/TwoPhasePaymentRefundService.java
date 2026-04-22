package study.distributedtransaction.payment.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import study.distributedtransaction.common.PaymentRefundRequest;
import study.distributedtransaction.common.TwoPhaseResponse;
import study.distributedtransaction.payment.domain.Payment;
import study.distributedtransaction.payment.domain.PaymentRepository;
import study.distributedtransaction.payment.domain.TwoPhasePaymentRefundOperation;
import study.distributedtransaction.payment.domain.TwoPhasePaymentRefundOperationRepository;
import study.distributedtransaction.payment.domain.TwoPhasePaymentStatus;

@Service
public class TwoPhasePaymentRefundService {

    private static final String PARTICIPANT = "payment-service";

    private final PaymentRepository paymentRepository;
    private final TwoPhasePaymentRefundOperationRepository operationRepository;

    public TwoPhasePaymentRefundService(
            PaymentRepository paymentRepository,
            TwoPhasePaymentRefundOperationRepository operationRepository
    ) {
        this.paymentRepository = paymentRepository;
        this.operationRepository = operationRepository;
    }

    @Transactional
    public TwoPhaseResponse prepare(PaymentRefundRequest request, boolean fail) {
        if (fail) {
            throw new IllegalStateException("Simulated payment prepare failure");
        }

        Payment payment = paymentRepository.findById(request.paymentId())
                .orElseThrow(() -> new IllegalArgumentException("Payment not found. paymentId=" + request.paymentId()));
        if (payment.getAmount().compareTo(request.amount()) != 0) {
            throw new IllegalArgumentException("Only full refund is supported in this skeleton.");
        }

        return operationRepository.findById(request.idempotencyKey())
                .map(operation -> new TwoPhaseResponse(
                        operation.getTransactionId(),
                        PARTICIPANT,
                        operation.getStatus().name(),
                        "prepare request is idempotent"
                ))
                .orElseGet(() -> {
                    operationRepository.save(new TwoPhasePaymentRefundOperation(
                            request.idempotencyKey(),
                            request.orderId(),
                            request.paymentId(),
                            request.amount()
                    ));
                    return new TwoPhaseResponse(request.idempotencyKey(), PARTICIPANT, "PREPARED", "refund prepared");
                });
    }

    @Transactional
    public TwoPhaseResponse commit(String transactionId, boolean fail) {
        if (fail) {
            throw new IllegalStateException("Simulated payment commit failure");
        }

        TwoPhasePaymentRefundOperation operation = find(transactionId);
        if (operation.getStatus() == TwoPhasePaymentStatus.COMMITTED) {
            return new TwoPhaseResponse(transactionId, PARTICIPANT, "COMMITTED", "commit request is idempotent");
        }
        if (operation.getStatus() == TwoPhasePaymentStatus.ROLLED_BACK) {
            throw new IllegalStateException("Cannot commit a rolled back payment operation. transactionId=" + transactionId);
        }

        Payment payment = paymentRepository.findById(operation.getPaymentId())
                .orElseThrow(() -> new IllegalArgumentException("Payment not found. paymentId=" + operation.getPaymentId()));
        payment.refund(operation.getAmount());
        operation.commit();

        return new TwoPhaseResponse(transactionId, PARTICIPANT, "COMMITTED", "payment refunded");
    }

    @Transactional
    public TwoPhaseResponse rollback(String transactionId) {
        TwoPhasePaymentRefundOperation operation = find(transactionId);
        if (operation.getStatus() == TwoPhasePaymentStatus.COMMITTED) {
            return new TwoPhaseResponse(transactionId, PARTICIPANT, "COMMITTED", "already committed; 2PC cannot compensate here");
        }
        if (operation.getStatus() == TwoPhasePaymentStatus.ROLLED_BACK) {
            return new TwoPhaseResponse(transactionId, PARTICIPANT, "ROLLED_BACK", "rollback request is idempotent");
        }

        operation.rollback();
        return new TwoPhaseResponse(transactionId, PARTICIPANT, "ROLLED_BACK", "prepared refund discarded");
    }

    private TwoPhasePaymentRefundOperation find(String transactionId) {
        return operationRepository.findById(transactionId)
                .orElseThrow(() -> new IllegalArgumentException("Payment 2PC operation not found. transactionId=" + transactionId));
    }
}
