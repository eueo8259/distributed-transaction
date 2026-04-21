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
    private final InventoryClient inventoryClient;
    private final PaymentClient paymentClient;

    public OrderCancelService(
            OrderStateService orderStateService,
            InventoryClient inventoryClient,
            PaymentClient paymentClient
    ) {
        this.orderStateService = orderStateService;
        this.inventoryClient = inventoryClient;
        this.paymentClient = paymentClient;
    }

    public CancelOrderResponse cancel(Long orderId, CancelFailurePoint failurePoint) {
        PurchaseOrder order = orderStateService.markCancelRequested(orderId);
        String operationId = "cancel-order-%d-%s".formatted(orderId, UUID.randomUUID());

        boolean inventoryRestored = false;
        boolean paymentRefunded = false;

        try {
            inventoryClient.restore(
                    new InventoryRestoreRequest(order.getId(), order.getSku(), order.getQuantity(), operationId),
                    failurePoint == CancelFailurePoint.INVENTORY
            );
            inventoryRestored = true;

            if (failurePoint == CancelFailurePoint.AFTER_INVENTORY) {
                throw new IllegalStateException("Simulated failure after inventory restore");
            }

            paymentClient.refund(
                    new PaymentRefundRequest(order.getId(), order.getPaymentId(), order.getAmount(), operationId),
                    failurePoint == CancelFailurePoint.PAYMENT
            );
            paymentRefunded = true;

            orderStateService.completeCancel(orderId);
            return new CancelOrderResponse(orderId, "CANCELLED", true, true, "cancel completed");
        } catch (RuntimeException exception) {
            orderStateService.failCancel(orderId);
            return new CancelOrderResponse(
                    orderId,
                    "CANCEL_FAILED",
                    inventoryRestored,
                    paymentRefunded,
                    "distributed flow failed: " + exception.getMessage()
            );
        }
    }
}
