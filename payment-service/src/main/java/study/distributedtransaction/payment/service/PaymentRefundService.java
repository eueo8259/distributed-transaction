package study.distributedtransaction.payment.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import study.distributedtransaction.common.PaymentRefundRequest;
import study.distributedtransaction.common.PaymentRefundResponse;
import study.distributedtransaction.payment.domain.Payment;
import study.distributedtransaction.payment.domain.PaymentRefundOperation;
import study.distributedtransaction.payment.domain.PaymentRefundOperationRepository;
import study.distributedtransaction.payment.domain.PaymentRepository;

@Service
public class PaymentRefundService {

    private final PaymentRepository paymentRepository;
    private final PaymentRefundOperationRepository operationRepository;

    public PaymentRefundService(
            PaymentRepository paymentRepository,
            PaymentRefundOperationRepository operationRepository
    ) {
        this.paymentRepository = paymentRepository;
        this.operationRepository = operationRepository;
    }

    @Transactional
    public PaymentRefundResponse refund(PaymentRefundRequest request, boolean fail) {
        if (fail) {
            throw new IllegalStateException("Simulated payment failure");
        }

        Payment payment = paymentRepository.findById(request.paymentId())
                .orElseThrow(() -> new IllegalArgumentException("Payment not found. paymentId=" + request.paymentId()));

        if (operationRepository.existsById(request.idempotencyKey())) {
            return new PaymentRefundResponse(
                    request.orderId(),
                    payment.getPaymentId(),
                    payment.getRefundedAmount(),
                    "ALREADY_REFUNDED"
            );
        }

        payment.refund(request.amount());
        operationRepository.save(new PaymentRefundOperation(
                request.idempotencyKey(),
                request.orderId(),
                request.paymentId(),
                request.amount()
        ));

        return new PaymentRefundResponse(
                request.orderId(),
                payment.getPaymentId(),
                payment.getRefundedAmount(),
                "REFUNDED"
        );
    }
}
