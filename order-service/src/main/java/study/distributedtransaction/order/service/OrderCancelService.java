package study.distributedtransaction.order.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import study.distributedtransaction.common.CancelOrderResponse;
import study.distributedtransaction.common.InventoryDeductRequest;
import study.distributedtransaction.common.InventoryRestoreRequest;
import study.distributedtransaction.common.PaymentRefundRequest;
import study.distributedtransaction.order.client.InventoryClient;
import study.distributedtransaction.order.client.PaymentClient;
import study.distributedtransaction.order.domain.CancelOrderSaga;
import study.distributedtransaction.order.domain.CancelOrderSagaRepository;
import study.distributedtransaction.order.domain.PurchaseOrder;

import java.util.List;
import java.util.UUID;

@Service
public class OrderCancelService {

    private final OrderStateService orderStateService;
    private final CancelOrderSagaRepository sagaRepository;
    private final CancelOrderSagaStateService sagaStateService;
    private final InventoryClient inventoryClient;
    private final PaymentClient paymentClient;

    public OrderCancelService(
            OrderStateService orderStateService,
            CancelOrderSagaRepository sagaRepository,
            CancelOrderSagaStateService sagaStateService,
            InventoryClient inventoryClient,
            PaymentClient paymentClient
    ) {
        this.orderStateService = orderStateService;
        this.sagaRepository = sagaRepository;
        this.sagaStateService = sagaStateService;
        this.inventoryClient = inventoryClient;
        this.paymentClient = paymentClient;
    }

    public CancelOrderResponse cancel(Long orderId, CancelFailurePoint failurePoint) {
        // order-service는 orchestration 방식에서 Saga orchestrator 역할을 맡는다.
        // participant는 자기 로컬 트랜잭션만 수행하고,
        // 다음 단계 호출과 보상 호출은 orchestrator가 직접 결정한다.
        PurchaseOrder order = orderStateService.markCancelRequested(orderId);
        String sagaId = "cancel-order-%d-%s".formatted(orderId, UUID.randomUUID());
        sagaRepository.save(new CancelOrderSaga(sagaId, orderId));

        boolean inventoryRestored = false;
        boolean paymentRefunded = false;

        try {
            inventoryClient.restore(
                    new InventoryRestoreRequest(order.getId(), order.getSku(), order.getQuantity(), sagaId + ":inventory-restore"),
                    failurePoint == CancelFailurePoint.INVENTORY
            );
            inventoryRestored = true;
            sagaStateService.markInventoryRestored(sagaId);

            if (failurePoint == CancelFailurePoint.AFTER_INVENTORY) {
                throw new IllegalStateException("Simulated failure after inventory restore");
            }

            paymentClient.refund(
                    new PaymentRefundRequest(order.getId(), order.getPaymentId(), order.getAmount(), sagaId + ":payment-refund"),
                    shouldFailPayment(failurePoint)
            );
            paymentRefunded = true;
            sagaStateService.markPaymentRefunded(sagaId);

            orderStateService.completeCancel(orderId);
            sagaStateService.markCompleted(sagaId);
            return new CancelOrderResponse(orderId, "CANCELLED", true, true, "orchestrated saga completed. sagaId=" + sagaId);
        } catch (RuntimeException exception) {
            if (inventoryRestored && !paymentRefunded) {
                compensateInventory(order, sagaId, failurePoint, exception.getMessage());
            } else {
                sagaStateService.markFailed(sagaId, exception.getMessage());
            }

            orderStateService.failCancel(orderId);
            return new CancelOrderResponse(
                    orderId,
                    "CANCEL_FAILED",
                    inventoryRestored,
                    paymentRefunded,
                    "orchestrated saga failed. sagaId=%s, reason=%s".formatted(sagaId, exception.getMessage())
            );
        }
    }

    @Transactional(readOnly = true)
    public List<CancelOrderSaga> findSagas() {
        return sagaRepository.findAll();
    }

    private void compensateInventory(PurchaseOrder order, String sagaId, CancelFailurePoint failurePoint, String reason) {
        try {
            inventoryClient.deduct(
                    new InventoryDeductRequest(order.getId(), order.getSku(), order.getQuantity(), sagaId + ":inventory-compensation"),
                    failurePoint == CancelFailurePoint.INVENTORY_COMPENSATION
            );
            sagaStateService.markInventoryCompensated(sagaId, reason);
        } catch (RuntimeException compensationException) {
            sagaStateService.markFailed(
                    sagaId,
                    "payment failed: %s, inventory compensation failed: %s".formatted(reason, compensationException.getMessage())
            );
        }
    }

    private boolean shouldFailPayment(CancelFailurePoint failurePoint) {
        // 보상 실패를 재현하려면 먼저 환불 단계에서 실패해 보상 흐름에 진입해야 한다.
        return failurePoint == CancelFailurePoint.PAYMENT
                || failurePoint == CancelFailurePoint.INVENTORY_COMPENSATION;
    }
}
