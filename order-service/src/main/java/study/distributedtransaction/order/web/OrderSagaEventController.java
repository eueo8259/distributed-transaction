package study.distributedtransaction.order.web;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import study.distributedtransaction.common.InventoryCompensatedEvent;
import study.distributedtransaction.common.InventoryRestoreFailedEvent;
import study.distributedtransaction.common.PaymentRefundedEvent;
import study.distributedtransaction.order.service.OrderStateService;

/**
 * order-service가 Choreography의 최종 결과 이벤트를 받는 엔드포인트다.
 * 즉 order-service 입장에서 "이벤트에 반응하는 쪽"을 담당한다.
 */
@RestController
public class OrderSagaEventController {

    private final OrderStateService orderStateService;

    public OrderSagaEventController(OrderStateService orderStateService) {
        this.orderStateService = orderStateService;
    }

    @PostMapping("/internal/events/inventory-restore-failed")
    public ResponseEntity<Void> handleInventoryRestoreFailed(@RequestBody InventoryRestoreFailedEvent event) {
        // 재고 복구 자체가 실패했으므로, Saga는 더 진행할 수 없고 바로 실패 처리한다.
        orderStateService.failCancel(event.orderId());
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/internal/events/payment-refunded")
    public ResponseEntity<Void> handlePaymentRefunded(@RequestBody PaymentRefundedEvent event) {
        // 마지막 비즈니스 단계인 환불이 성공했으므로, order-service가 로컬 상태를 최종 완료로 마무리한다.
        orderStateService.completeCancel(event.orderId());
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/internal/events/inventory-compensated")
    public ResponseEntity<Void> handleInventoryCompensated(@RequestBody InventoryCompensatedEvent event) {
        // 보상 트랜잭션까지 끝났으므로 주문은 취소 실패 상태로 남는다.
        orderStateService.failCancel(event.orderId());
        return ResponseEntity.accepted().build();
    }
}
