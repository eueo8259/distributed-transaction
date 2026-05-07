package study.distributedtransaction.inventory.service;

import org.springframework.stereotype.Service;
import study.distributedtransaction.common.InventoryCompensatedEvent;
import study.distributedtransaction.common.InventoryRestoreFailedEvent;
import study.distributedtransaction.common.InventoryRestoredEvent;
import study.distributedtransaction.common.OrderCancelRequestedEvent;
import study.distributedtransaction.common.PaymentRefundFailedEvent;
import study.distributedtransaction.inventory.client.OrderEventClient;
import study.distributedtransaction.inventory.client.PaymentEventClient;

/**
 * Choreography Saga에서 inventory-service 쪽 로컬 트랜잭션들을 담당한다.
 *
 * 중요 포인트:
 * - 각 메서드는 inventory-service 내부의 로컬 트랜잭션 경계다.
 * - 분산 트랜잭션 전체를 감싸는 하나의 Spring 트랜잭션은 없다.
 * - 여러 로컬 트랜잭션을 이벤트 체인으로 연결해서 Saga를 만든다.
 */
@Service
public class InventorySagaService {

    private final InventoryLocalTransactionService inventoryLocalTransactionService;
    private final PaymentEventClient paymentEventClient;
    private final OrderEventClient orderEventClient;

    public InventorySagaService(
            InventoryLocalTransactionService inventoryLocalTransactionService,
            PaymentEventClient paymentEventClient,
            OrderEventClient orderEventClient
    ) {
        this.inventoryLocalTransactionService = inventoryLocalTransactionService;
        this.paymentEventClient = paymentEventClient;
        this.orderEventClient = orderEventClient;
    }

    public void handleOrderCancelRequested(OrderCancelRequestedEvent event) {
        try {
            inventoryLocalTransactionService.restoreInventory(event);
            // 여기 시점에는 재고 복구 로컬 트랜잭션이 이미 커밋된 상태다.
            // 그 다음 이벤트를 발행해서 Saga의 다음 단계를 이어간다.
            paymentEventClient.publishInventoryRestored(createInventoryRestoredEvent(event));
        } catch (RuntimeException exception) {
            // 재고 복구 단계가 실패했으므로 Saga는 다음 단계로 진행하지 못한다.
            orderEventClient.publishInventoryRestoreFailed(createInventoryRestoreFailedEvent(event, exception));
        }
    }

    public void handlePaymentRefundFailed(PaymentRefundFailedEvent event) {
        inventoryLocalTransactionService.compensateInventory(event);
        // 이 보상도 하나의 새로운 로컬 트랜잭션이다.
        // 예전에 커밋된 재고 복구를 "롤백"하는 것이 아니라 반대 동작을 새로 수행한다.
        orderEventClient.publishInventoryCompensated(createInventoryCompensatedEvent(event));
    }

    private InventoryRestoredEvent createInventoryRestoredEvent(OrderCancelRequestedEvent event) {
        return new InventoryRestoredEvent(
                event.sagaId(),
                event.orderId(),
                event.sku(),
                event.quantity(),
                event.paymentId(),
                event.amount(),
                event.failurePoint()
        );
    }

    private InventoryRestoreFailedEvent createInventoryRestoreFailedEvent(
            OrderCancelRequestedEvent event,
            RuntimeException exception
    ) {
        return new InventoryRestoreFailedEvent(
                event.sagaId(),
                event.orderId(),
                exception.getMessage()
        );
    }

    private InventoryCompensatedEvent createInventoryCompensatedEvent(PaymentRefundFailedEvent event) {
        return new InventoryCompensatedEvent(
                event.sagaId(),
                event.orderId(),
                event.sku(),
                event.quantity()
        );
    }
}
