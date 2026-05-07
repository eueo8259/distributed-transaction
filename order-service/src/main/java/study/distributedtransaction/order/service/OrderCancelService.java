package study.distributedtransaction.order.service;

import org.springframework.stereotype.Service;
import study.distributedtransaction.common.CancelOrderResponse;
import study.distributedtransaction.common.OrderCancelRequestedEvent;
import study.distributedtransaction.order.client.InventoryEventClient;
import study.distributedtransaction.order.domain.PurchaseOrder;

import java.util.UUID;

/**
 * Choreography 브랜치에서 order-service는 다음 비즈니스 단계를 직접 호출하지 않는다.
 * 대신 자신의 로컬 트랜잭션을 수행한 뒤 첫 번째 Saga 이벤트를 발행한다.
 */
@Service
public class OrderCancelService {

    private final OrderStateService orderStateService;
    private final InventoryEventClient inventoryEventClient;

    public OrderCancelService(
            OrderStateService orderStateService,
            InventoryEventClient inventoryEventClient
    ) {
        this.orderStateService = orderStateService;
        this.inventoryEventClient = inventoryEventClient;
    }

    public CancelOrderResponse cancel(Long orderId, CancelFailurePoint failurePoint) {
        // 1. 여기서는 order-service 자신의 DB만 변경한다.
        // 분산 트랜잭션 전체가 아니라 order-service의 로컬 트랜잭션 경계다.
        PurchaseOrder order = orderStateService.markCancelRequested(orderId);
        String sagaId = "cancel-order-%d-%s".formatted(orderId, UUID.randomUUID());

        // 2. 로컬 트랜잭션이 성공적으로 끝난 뒤 첫 번째 이벤트를 발행한다.
        // 이 이벤트가 inventory-service, payment-service로 이어지는 분산 Saga의 시작점이 된다.
        inventoryEventClient.publishOrderCancelRequested(createOrderCancelRequestedEvent(order, sagaId, failurePoint));

        // 3. 이 시점에는 아직 최종 성공/실패를 모른다.
        // 주문은 일단 CANCEL_REQUESTED 상태이고, 이후 이벤트 반응에 따라 최종 상태가 결정된다.
        return new CancelOrderResponse(orderId, "CANCEL_REQUESTED", false, false, "saga started. sagaId=" + sagaId);
    }

    private OrderCancelRequestedEvent createOrderCancelRequestedEvent(
            PurchaseOrder order,
            String sagaId,
            CancelFailurePoint failurePoint
    ) {
        return new OrderCancelRequestedEvent(
                sagaId,
                order.getId(),
                order.getSku(),
                order.getQuantity(),
                order.getPaymentId(),
                order.getAmount(),
                failurePoint.name()
        );
    }
}
