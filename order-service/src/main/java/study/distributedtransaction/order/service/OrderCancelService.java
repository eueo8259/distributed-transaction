package study.distributedtransaction.order.service;

import org.springframework.stereotype.Service;
import study.distributedtransaction.common.CancelOrderResponse;
import study.distributedtransaction.common.InventoryRestoreRequest;
import study.distributedtransaction.common.PaymentRefundRequest;
import study.distributedtransaction.order.client.InventoryClient;
import study.distributedtransaction.order.client.PaymentClient;
import study.distributedtransaction.order.domain.PurchaseOrder;

import java.util.UUID;

@Service
public class OrderCancelService {

    private final OrderStateService orderStateService;
    private final TwoPhaseCancelLogService transactionLogService;
    private final InventoryClient inventoryClient;
    private final PaymentClient paymentClient;

    public OrderCancelService(
            OrderStateService orderStateService,
            TwoPhaseCancelLogService transactionLogService,
            InventoryClient inventoryClient,
            PaymentClient paymentClient
    ) {
        this.orderStateService = orderStateService;
        this.transactionLogService = transactionLogService;
        this.inventoryClient = inventoryClient;
        this.paymentClient = paymentClient;
    }

    public CancelOrderResponse cancel(Long orderId, CancelFailurePoint failurePoint) {
        PurchaseOrder order = orderStateService.markCancelRequested(orderId);
        String transactionId = "cancel-order-%d-%s".formatted(orderId, UUID.randomUUID());
        transactionLogService.start(transactionId, orderId);

        boolean inventoryPrepared = false;
        boolean paymentPrepared = false;
        boolean inventoryCommitted = false;
        boolean paymentCommitted = false;

        try {
            inventoryClient.prepareRestore(
                    new InventoryRestoreRequest(order.getId(), order.getSku(), order.getQuantity(), transactionId),
                    failurePoint == CancelFailurePoint.INVENTORY || failurePoint == CancelFailurePoint.INVENTORY_PREPARE
            );
            inventoryPrepared = true;
            transactionLogService.markInventoryPrepared(transactionId);

            paymentClient.prepareRefund(
                    new PaymentRefundRequest(order.getId(), order.getPaymentId(), order.getAmount(), transactionId),
                    failurePoint == CancelFailurePoint.PAYMENT || failurePoint == CancelFailurePoint.PAYMENT_PREPARE
            );
            paymentPrepared = true;
            transactionLogService.markPaymentPrepared(transactionId);
            transactionLogService.markPrepared(transactionId);

            if (failurePoint == CancelFailurePoint.AFTER_PREPARE || failurePoint == CancelFailurePoint.AFTER_INVENTORY) {
                throw new IllegalStateException("Simulated coordinator failure after prepare phase");
            }

            inventoryClient.commitRestore(transactionId, failurePoint == CancelFailurePoint.INVENTORY_COMMIT);
            inventoryCommitted = true;
            transactionLogService.markInventoryCommitted(transactionId);

            paymentClient.commitRefund(transactionId, failurePoint == CancelFailurePoint.PAYMENT_COMMIT);
            paymentCommitted = true;
            transactionLogService.markPaymentCommitted(transactionId);

            orderStateService.completeCancel(orderId);
            transactionLogService.markCommitted(transactionId);
            return new CancelOrderResponse(orderId, "CANCELLED", true, true, "2PC commit completed. transactionId=" + transactionId);
        } catch (RuntimeException exception) {
            rollbackPreparedParticipants(transactionId, inventoryPrepared, paymentPrepared, inventoryCommitted, paymentCommitted);
            if (inventoryCommitted || paymentCommitted) {
                transactionLogService.markFailed(transactionId, exception.getMessage());
            } else {
                transactionLogService.markRolledBack(transactionId, exception.getMessage());
            }
            orderStateService.failCancel(orderId);
            return new CancelOrderResponse(
                    orderId,
                    "CANCEL_FAILED",
                    inventoryCommitted,
                    paymentCommitted,
                    "2PC flow failed. transactionId=%s, reason=%s".formatted(transactionId, exception.getMessage())
            );
        }
    }

    private void rollbackPreparedParticipants(
            String transactionId,
            boolean inventoryPrepared,
            boolean paymentPrepared,
            boolean inventoryCommitted,
            boolean paymentCommitted
    ) {
        if (paymentPrepared && !paymentCommitted) {
            paymentClient.rollbackRefund(transactionId);
        }
        if (inventoryPrepared && !inventoryCommitted) {
            inventoryClient.rollbackRestore(transactionId);
        }
    }
}
