package study.distributedtransaction.payment.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import study.distributedtransaction.common.InventoryRestoredEvent;
import study.distributedtransaction.payment.domain.Payment;
import study.distributedtransaction.payment.domain.PaymentRefundOperation;
import study.distributedtransaction.payment.domain.PaymentRefundOperationRepository;
import study.distributedtransaction.payment.domain.PaymentRepository;

/**
 * payment-service 안에서 실제 환불 DB 변경을 담당하는 로컬 트랜잭션 서비스다.
 *
 * Saga 흐름 제어 서비스와 분리해 Spring 프록시 기반 @Transactional이
 * self-invocation 문제 없이 적용되도록 만든다.
 */
@Service
public class PaymentLocalTransactionService {

    private final PaymentRepository paymentRepository;
    private final PaymentRefundOperationRepository operationRepository;

    public PaymentLocalTransactionService(
            PaymentRepository paymentRepository,
            PaymentRefundOperationRepository operationRepository
    ) {
        this.paymentRepository = paymentRepository;
        this.operationRepository = operationRepository;
    }

    @Transactional
    public void refund(InventoryRestoredEvent event) {
        // payment-service 내부에서만 열리는 로컬 트랜잭션이다.
        // 보상 트랜잭션 실패를 재현하려면 먼저 환불 단계가 실패해서
        // inventory-service의 보상 단계로 진입해야 한다.
        if ("PAYMENT_REFUND".equalsIgnoreCase(event.failurePoint())
                || "INVENTORY_COMPENSATE".equalsIgnoreCase(event.failurePoint())) {
            throw new IllegalStateException("Simulated payment refund failure");
        }

        Payment payment = paymentRepository.findById(event.paymentId())
                .orElseThrow(() -> new IllegalArgumentException("Payment not found. paymentId=" + event.paymentId()));

        if (operationRepository.existsById(event.sagaId())) {
            return;
        }

        payment.refund(event.amount());
        operationRepository.save(new PaymentRefundOperation(
                event.sagaId(),
                event.orderId(),
                event.paymentId(),
                event.amount()
        ));
    }
}
