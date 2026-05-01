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

        // 현재 예제의 prepare는 짧은 로컬 트랜잭션으로 결제 정보만 검증하고 pending operation을 저장한다.
        // 따라서 이 메서드가 반환된 뒤 H2의 실제 row lock은 유지되지 않는다.
        // 실제 XA 기반 2PC라면 prepare 시점에 잡은 결제 row lock이 coordinator의 commit/rollback 결정까지 유지될 수 있다.
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
                    // pending operation은 이 예제에서 "논리적 락" 역할을 한다.
                    // 같은 transactionId가 다시 들어오면 중복 환불을 막고, commit/rollback 전 상태를 관찰할 수 있다.
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
        // commit의 로컬 트랜잭션 안에서 실제 결제 row를 환불 처리하고 operation 상태를 COMMITTED로 바꾼다.
        // 이 짧은 구간에는 일반적인 DB write lock이 걸리지만, 메서드가 끝나면 해제된다.
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

        // prepare에서 실제 환불을 하지 않았기 때문에 rollback은 pending operation만 ROLLED_BACK으로 바꾼다.
        // 실제 XA 2PC에서는 이 결정이 와야 prepare 상태에서 잡고 있던 DB lock을 풀 수 있다.
        operation.rollback();
        return new TwoPhaseResponse(transactionId, PARTICIPANT, "ROLLED_BACK", "prepared refund discarded");
    }

    private TwoPhasePaymentRefundOperation find(String transactionId) {
        return operationRepository.findById(transactionId)
                .orElseThrow(() -> new IllegalArgumentException("Payment 2PC operation not found. transactionId=" + transactionId));
    }
}
