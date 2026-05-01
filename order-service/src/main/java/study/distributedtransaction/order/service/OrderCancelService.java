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
        // 1. coordinator가 주문 취소 흐름을 시작하고, 주문을 취소 요청 상태로 변경한다.
        // order-service의 주문 DB 트랜잭션은 OrderStateService 안에서 짧게 열리고 닫힌다.
        // 실제 XA 기반 2PC라면 주문 row lock이 최종 commit/rollback 결정까지 유지될 수 있지만,
        // 이 예제는 REST 기반 학습 코드라 긴 DB lock 대신 상태값(CANCEL_REQUESTED)으로 진행 중임을 표현한다.
        PurchaseOrder order = orderStateService.markCancelRequested(orderId);

        // 2. 여러 서비스가 같은 작업을 바라볼 수 있도록 2PC 트랜잭션 ID를 만든다.
        String transactionId = "cancel-order-%d-%s".formatted(orderId, UUID.randomUUID());

        // 3. coordinator 로컬 DB에 2PC 진행 로그를 남긴다.
        transactionLogService.start(transactionId, orderId);

        // 4. 실패 시 어떤 participant까지 prepare/commit 되었는지 판단하기 위한 플래그다.
        boolean inventoryPrepared = false;
        boolean paymentPrepared = false;
        boolean inventoryCommitted = false;
        boolean paymentCommitted = false;

        try {
            // 5. prepare phase: inventory-service가 재고 복구를 할 수 있는지 확인하고 pending 상태로 기록한다.
            // coordinator는 이 HTTP 응답을 기다린다. 실제 2PC에서 participant가 DB lock을 잡고 prepare 상태로
            // 대기한다면, 이 구간의 지연이 곧 전체 트랜잭션 지연과 lock 유지 시간으로 이어진다.
            inventoryClient.prepareRestore(
                    new InventoryRestoreRequest(order.getId(), order.getSku(), order.getQuantity(), transactionId),
                    failurePoint == CancelFailurePoint.INVENTORY || failurePoint == CancelFailurePoint.INVENTORY_PREPARE
            );
            inventoryPrepared = true;
            transactionLogService.markInventoryPrepared(transactionId);

            // 6. prepare phase: payment-service가 환불을 할 수 있는지 확인하고 pending 상태로 기록한다.
            // 외부 결제 API가 느리거나 2PC를 지원하지 않으면 여기서 전체 흐름이 가장 느린 participant에 맞춰진다.
            // 그래서 실무 MSA에서는 결제 같은 외부 시스템을 전통적인 2PC participant로 묶기 어렵다.
            paymentClient.prepareRefund(
                    new PaymentRefundRequest(order.getId(), order.getPaymentId(), order.getAmount(), transactionId),
                    failurePoint == CancelFailurePoint.PAYMENT || failurePoint == CancelFailurePoint.PAYMENT_PREPARE
            );
            paymentPrepared = true;
            transactionLogService.markPaymentPrepared(transactionId);
            transactionLogService.markPrepared(transactionId);

            // 7. 두 participant가 모두 prepare 된 뒤 coordinator가 죽는 상황을 실험하기 위한 실패 지점이다.
            if (failurePoint == CancelFailurePoint.AFTER_PREPARE || failurePoint == CancelFailurePoint.AFTER_INVENTORY) {
                throw new IllegalStateException("Simulated coordinator failure after prepare phase");
            }

            // 8. commit phase: 모든 prepare가 성공했으므로 inventory-service에 실제 재고 복구를 지시한다.
            inventoryClient.commitRestore(transactionId, failurePoint == CancelFailurePoint.INVENTORY_COMMIT);
            inventoryCommitted = true;
            transactionLogService.markInventoryCommitted(transactionId);

            // 9. commit phase: payment-service에 실제 환불을 지시한다.
            paymentClient.commitRefund(transactionId, failurePoint == CancelFailurePoint.PAYMENT_COMMIT);
            paymentCommitted = true;
            transactionLogService.markPaymentCommitted(transactionId);

            // 10. 모든 participant commit이 끝난 뒤 주문을 최종 취소 완료 상태로 변경한다.
            orderStateService.completeCancel(orderId);
            transactionLogService.markCommitted(transactionId);
            return new CancelOrderResponse(orderId, "CANCELLED", true, true, "2PC commit completed. transactionId=" + transactionId);
        } catch (RuntimeException exception) {
            // 11. prepare만 끝난 participant는 실제 반영 전이므로 rollback으로 pending 작업을 버릴 수 있다.
            rollbackPreparedParticipants(transactionId, inventoryPrepared, paymentPrepared, inventoryCommitted, paymentCommitted);

            // 12. 이미 commit된 participant가 있으면 단순 rollback이 불가능하므로 FAILED로 남겨 복구 대상임을 표시한다.
            if (inventoryCommitted || paymentCommitted) {
                transactionLogService.markFailed(transactionId, exception.getMessage());
            } else {
                transactionLogService.markRolledBack(transactionId, exception.getMessage());
            }

            // 13. 주문은 취소 실패 상태로 바꾸고, 어디까지 commit 되었는지 응답에 담아준다.
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
        // commit 전 payment prepare 작업은 실제 환불 전이므로 안전하게 rollback할 수 있다.
        if (paymentPrepared && !paymentCommitted) {
            paymentClient.rollbackRefund(transactionId);
        }
        // commit 전 inventory prepare 작업은 실제 재고 복구 전이므로 안전하게 rollback할 수 있다.
        if (inventoryPrepared && !inventoryCommitted) {
            inventoryClient.rollbackRestore(transactionId);
        }
    }
}
