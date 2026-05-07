package study.distributedtransaction.inventory.web;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import study.distributedtransaction.common.OrderCancelRequestedEvent;
import study.distributedtransaction.common.PaymentRefundFailedEvent;
import study.distributedtransaction.inventory.service.InventorySagaService;

/**
 * inventory-service가 Saga 이벤트에 반응하는 입구다.
 * 각 엔드포인트는 이벤트를 받아 로컬 트랜잭션을 실행하고, 그다음 이벤트를 발행한다.
 */
@RestController
public class InventorySagaEventController {

    private final InventorySagaService inventorySagaService;

    public InventorySagaEventController(InventorySagaService inventorySagaService) {
        this.inventorySagaService = inventorySagaService;
    }

    @PostMapping("/internal/events/order-cancel-requested")
    public ResponseEntity<Void> handleOrderCancelRequested(@RequestBody OrderCancelRequestedEvent event) {
        inventorySagaService.handleOrderCancelRequested(event);
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/internal/events/payment-refund-failed")
    public ResponseEntity<Void> handlePaymentRefundFailed(@RequestBody PaymentRefundFailedEvent event) {
        inventorySagaService.handlePaymentRefundFailed(event);
        return ResponseEntity.accepted().build();
    }
}
