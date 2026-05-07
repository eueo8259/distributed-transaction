package study.distributedtransaction.payment.service;

import org.springframework.stereotype.Service;
import study.distributedtransaction.common.InventoryRestoredEvent;
import study.distributedtransaction.common.PaymentRefundFailedEvent;
import study.distributedtransaction.common.PaymentRefundedEvent;
import study.distributedtransaction.payment.client.InventoryEventClient;
import study.distributedtransaction.payment.client.OrderEventClient;

/**
 * payment-service가 환불 로컬 트랜잭션을 수행한다.
 * 환불에 실패하면 inventory-service가 보상 트랜잭션을 수행할 수 있도록 실패 이벤트를 발행한다.
 */
@Service
public class PaymentSagaService {

    private final PaymentLocalTransactionService paymentLocalTransactionService;
    private final OrderEventClient orderEventClient;
    private final InventoryEventClient inventoryEventClient;

    public PaymentSagaService(
            PaymentLocalTransactionService paymentLocalTransactionService,
            OrderEventClient orderEventClient,
            InventoryEventClient inventoryEventClient
    ) {
        this.paymentLocalTransactionService = paymentLocalTransactionService;
        this.orderEventClient = orderEventClient;
        this.inventoryEventClient = inventoryEventClient;
    }

    public void handleInventoryRestored(InventoryRestoredEvent event) {
        try {
            paymentLocalTransactionService.refund(event);
            // 환불 로컬 트랜잭션이 성공적으로 커밋되었으므로
            // order-service가 최종 비즈니스 흐름을 완료할 수 있다.
            orderEventClient.publishPaymentRefunded(createPaymentRefundedEvent(event));
        } catch (RuntimeException exception) {
            // 재고 복구는 이미 커밋된 뒤라서 여기서 inventory를 직접 롤백할 수는 없다.
            // 대신 실패 이벤트를 발행해 inventory-service가 보상 트랜잭션을 수행하게 만든다.
            inventoryEventClient.publishPaymentRefundFailed(createPaymentRefundFailedEvent(event, exception));
        }
    }

    private PaymentRefundedEvent createPaymentRefundedEvent(InventoryRestoredEvent event) {
        return new PaymentRefundedEvent(
                event.sagaId(),
                event.orderId(),
                event.paymentId(),
                event.amount()
        );
    }

    private PaymentRefundFailedEvent createPaymentRefundFailedEvent(
            InventoryRestoredEvent event,
            RuntimeException exception
    ) {
        return new PaymentRefundFailedEvent(
                event.sagaId(),
                event.orderId(),
                event.sku(),
                event.quantity(),
                event.paymentId(),
                event.amount(),
                exception.getMessage(),
                event.failurePoint()
        );
    }
}
