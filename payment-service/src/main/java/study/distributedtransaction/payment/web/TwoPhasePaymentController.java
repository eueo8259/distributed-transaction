package study.distributedtransaction.payment.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import study.distributedtransaction.common.PaymentRefundRequest;
import study.distributedtransaction.common.TwoPhaseDecisionRequest;
import study.distributedtransaction.common.TwoPhaseResponse;
import study.distributedtransaction.payment.domain.TwoPhasePaymentRefundOperation;
import study.distributedtransaction.payment.domain.TwoPhasePaymentRefundOperationRepository;
import study.distributedtransaction.payment.service.TwoPhasePaymentRefundService;

import java.util.List;

@RestController
public class TwoPhasePaymentController {

    private final TwoPhasePaymentRefundService refundService;
    private final TwoPhasePaymentRefundOperationRepository operationRepository;

    public TwoPhasePaymentController(
            TwoPhasePaymentRefundService refundService,
            TwoPhasePaymentRefundOperationRepository operationRepository
    ) {
        this.refundService = refundService;
        this.operationRepository = operationRepository;
    }

    @PostMapping("/payments/3pc/prepare")
    public TwoPhaseResponse prepare(
            @RequestBody PaymentRefundRequest request,
            @RequestParam(defaultValue = "false") boolean fail
    ) {
        return refundService.prepare(request, fail);
    }

    @PostMapping("/payments/3pc/pre-commit")
    public TwoPhaseResponse preCommit(
            @RequestBody TwoPhaseDecisionRequest request,
            @RequestParam(defaultValue = "false") boolean fail
    ) {
        return refundService.preCommit(request.transactionId(), fail);
    }

    @PostMapping("/payments/3pc/commit")
    public TwoPhaseResponse commit(
            @RequestBody TwoPhaseDecisionRequest request,
            @RequestParam(defaultValue = "false") boolean fail
    ) {
        return refundService.commit(request.transactionId(), fail);
    }

    @PostMapping("/payments/3pc/rollback")
    public TwoPhaseResponse rollback(@RequestBody TwoPhaseDecisionRequest request) {
        return refundService.rollback(request.transactionId());
    }

    @GetMapping("/payments/3pc/operations")
    public List<TwoPhasePaymentRefundOperation> findOperations() {
        return operationRepository.findAll();
    }
}
