package study.distributedtransaction.payment.web;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import study.distributedtransaction.common.InventoryRestoredEvent;
import study.distributedtransaction.payment.service.PaymentSagaService;

/**
 * payment-service가 재고 복구 성공 이벤트에 반응하는 입구다.
 */
@RestController
public class PaymentSagaEventController {

    private final PaymentSagaService paymentSagaService;

    public PaymentSagaEventController(PaymentSagaService paymentSagaService) {
        this.paymentSagaService = paymentSagaService;
    }

    @PostMapping("/internal/events/inventory-restored")
    public ResponseEntity<Void> handleInventoryRestored(@RequestBody InventoryRestoredEvent event) {
        paymentSagaService.handleInventoryRestored(event);
        return ResponseEntity.accepted().build();
    }
}
