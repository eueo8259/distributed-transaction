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
    private final InventoryClient inventoryClient;
    private final PaymentClient paymentClient;

    public OrderCancelService(
            OrderStateService orderStateService,
            CancelOrderSagaRepository sagaRepository,
            InventoryClient inventoryClient,
            PaymentClient paymentClient
    ) {
        this.orderStateService = orderStateService;
        this.sagaRepository = sagaRepository;
        this.inventoryClient = inventoryClient;
        this.paymentClient = paymentClient;
    }

    public CancelOrderResponse cancel(Long orderId, CancelFailurePoint failurePoint) {
        // Orchestration 방식에서는 order-service가 Saga orchestrator 역할을 한다.
        // 각 서비스는 자기 로컬 트랜잭션만 수행하고, 다음 단계와 보상 여부는 orchestrator가 명시적으로 결정한다.
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
            markInventoryRestored(sagaId);

            if (failurePoint == CancelFailurePoint.AFTER_INVENTORY) {
                throw new IllegalStateException("Simulated failure after inventory restore");
            }

            paymentClient.refund(
                    new PaymentRefundRequest(order.getId(), order.getPaymentId(), order.getAmount(), sagaId + ":payment-refund"),
                    failurePoint == CancelFailurePoint.PAYMENT
            );
            paymentRefunded = true;
            markPaymentRefunded(sagaId);

            orderStateService.completeCancel(orderId);
            markCompleted(sagaId);
            return new CancelOrderResponse(orderId, "CANCELLED", true, true, "orchestrated saga completed. sagaId=" + sagaId);
        } catch (RuntimeException exception) {
            if (inventoryRestored && !paymentRefunded) {
                compensateInventory(order, sagaId, failurePoint, exception.getMessage());
            } else {
                markFailed(sagaId, exception.getMessage());
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

    @Transactional
    public void markInventoryRestored(String sagaId) {
        find(sagaId).markInventoryRestored();
    }

    @Transactional
    public void markPaymentRefunded(String sagaId) {
        find(sagaId).markPaymentRefunded();
    }

    @Transactional
    public void markCompleted(String sagaId) {
        find(sagaId).markCompleted();
    }

    @Transactional
    public void markInventoryCompensated(String sagaId, String reason) {
        find(sagaId).markInventoryCompensated(reason);
    }

    @Transactional
    public void markFailed(String sagaId, String reason) {
        find(sagaId).markFailed(reason);
    }

    private void compensateInventory(PurchaseOrder order, String sagaId, CancelFailurePoint failurePoint, String reason) {
        try {
            inventoryClient.deduct(
                    new InventoryDeductRequest(order.getId(), order.getSku(), order.getQuantity(), sagaId + ":inventory-compensation"),
                    failurePoint == CancelFailurePoint.INVENTORY_COMPENSATION
            );
            markInventoryCompensated(sagaId, reason);
        } catch (RuntimeException compensationException) {
            markFailed(sagaId, "payment failed: %s, inventory compensation failed: %s".formatted(reason, compensationException.getMessage()));
        }
    }

    private CancelOrderSaga find(String sagaId) {
        return sagaRepository.findById(sagaId)
                .orElseThrow(() -> new IllegalArgumentException("Cancel order saga not found. sagaId=" + sagaId));
    }
}
