package study.distributedtransaction.payment.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import study.distributedtransaction.common.PaymentRefundRequest;
import study.distributedtransaction.common.PaymentRefundResponse;
import study.distributedtransaction.payment.domain.Payment;
import study.distributedtransaction.payment.domain.PaymentRepository;
import study.distributedtransaction.payment.service.PaymentRefundService;

import java.util.List;

@RestController
public class PaymentController {

    private final PaymentRepository paymentRepository;
    private final PaymentRefundService refundService;

    public PaymentController(PaymentRepository paymentRepository, PaymentRefundService refundService) {
        this.paymentRepository = paymentRepository;
        this.refundService = refundService;
    }

    @GetMapping("/payments")
    public List<Payment> findAll() {
        return paymentRepository.findAll();
    }

    @PostMapping("/payments/refund")
    public PaymentRefundResponse refund(
            @RequestBody PaymentRefundRequest request,
            @RequestParam(defaultValue = "false") boolean fail
    ) {
        return refundService.refund(request, fail);
    }
}
