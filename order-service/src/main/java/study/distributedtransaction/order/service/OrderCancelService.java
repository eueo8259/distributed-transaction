package study.distributedtransaction.order.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import study.distributedtransaction.common.CancelOrderResponse;
import study.distributedtransaction.common.InventoryRestoreRequest;
import study.distributedtransaction.common.PaymentRefundRequest;
import study.distributedtransaction.order.client.InventoryClient;
import study.distributedtransaction.order.client.PaymentClient;
import study.distributedtransaction.order.domain.PurchaseOrder;
import study.distributedtransaction.order.domain.PurchaseOrderRepository;

import java.util.UUID;

@Service
public class OrderCancelService {

    private final PurchaseOrderRepository orderRepository;
    private final InventoryClient inventoryClient;
    private final PaymentClient paymentClient;

    public OrderCancelService(
            PurchaseOrderRepository orderRepository,
            InventoryClient inventoryClient,
            PaymentClient paymentClient
    ) {
        this.orderRepository = orderRepository;
        this.inventoryClient = inventoryClient;
        this.paymentClient = paymentClient;
    }

    public CancelOrderResponse cancel(Long orderId, CancelFailurePoint failurePoint) {
        PurchaseOrder order = markCancelRequested(orderId);
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

            completeCancel(orderId);
            return new CancelOrderResponse(orderId, "CANCELLED", true, true, "cancel completed");
        } catch (RuntimeException exception) {
            failCancel(orderId);
            return new CancelOrderResponse(
                    orderId,
                    "CANCEL_FAILED",
                    inventoryRestored,
                    paymentRefunded,
                    "distributed flow failed: " + exception.getMessage()
            );
        }
    }

    @Transactional
    public PurchaseOrder markCancelRequested(Long orderId) {
        PurchaseOrder order = findOrder(orderId);
        order.requestCancel();
        return order;
    }

    @Transactional
    public void completeCancel(Long orderId) {
        findOrder(orderId).completeCancel();
    }

    @Transactional
    public void failCancel(Long orderId) {
        findOrder(orderId).failCancel();
    }

    private PurchaseOrder findOrder(Long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found. orderId=" + orderId));
    }
}
