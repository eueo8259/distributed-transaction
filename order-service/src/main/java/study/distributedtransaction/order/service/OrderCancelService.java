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
        // 1. coordinator(order-service)가 주문 취소 흐름을 시작한다.
        // 주문 DB 트랜잭션은 OrderStateService 안에서 짧게 끝나며,
        // 이 예제는 실제 XA lock 대신 주문 상태와 3PC 로그로 진행 상황을 표현한다.
        PurchaseOrder order = orderStateService.markCancelRequested(orderId);

        // 2. 모든 participant가 같은 취소 작업을 식별할 수 있도록 글로벌 트랜잭션 ID를 만든다.
        String transactionId = "cancel-order-%d-%s".formatted(orderId, UUID.randomUUID());

        // 3. coordinator 로컬 로그를 먼저 남긴다.
        // coordinator가 중간에 죽었을 때 어디까지 진행했는지 판단하려면 이 로그가 기준이 된다.
        transactionLogService.start(transactionId, orderId);

        // 4. 각 participant가 어느 단계까지 도달했는지 catch 블록에서 판단하기 위한 플래그다.
        // 3PC에서는 PREPARED와 PRE_COMMITTED의 의미가 다르기 때문에 둘을 분리해서 기록한다.
        boolean inventoryPrepared = false;
        boolean paymentPrepared = false;
        boolean inventoryPreCommitted = false;
        boolean paymentPreCommitted = false;
        boolean inventoryCommitted = false;
        boolean paymentCommitted = false;

        try {
            // 5. canCommit/prepare phase.
            // participant가 작업 가능 여부를 검증하고 pending operation을 저장한다.
            // 이 단계까지는 coordinator가 실패 결정을 내리면 rollback으로 pending 작업을 버릴 수 있다.
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

            // 6. prepare 직후 coordinator 장애를 실험하는 지점이다.
            // 아직 preCommit 전이므로 participant는 명시적인 rollback 요청을 받으면 안전하게 취소할 수 있다.
            if (failurePoint == CancelFailurePoint.AFTER_PREPARE || failurePoint == CancelFailurePoint.AFTER_INVENTORY) {
                throw new IllegalStateException("Simulated coordinator failure after prepare phase");
            }

            // 7. preCommit phase.
            // 2PC에는 없는 3PC의 중간 단계다. coordinator가 "모두 prepare 됐으니 commit 방향으로 갈 것"을 알린다.
            // participant는 PRE_COMMITTED 상태를 기록하고, 이후 coordinator가 사라지면 timeout 후 자율 commit할 수 있다.
            inventoryClient.preCommitRestore(transactionId, failurePoint == CancelFailurePoint.INVENTORY_PRE_COMMIT);
            inventoryPreCommitted = true;
            transactionLogService.markInventoryPreCommitted(transactionId);

            paymentClient.preCommitRefund(transactionId, failurePoint == CancelFailurePoint.PAYMENT_PRE_COMMIT);
            paymentPreCommitted = true;
            transactionLogService.markPaymentPreCommitted(transactionId);
            transactionLogService.markPreCommitted(transactionId);

            // 8. preCommit 직후 coordinator 장애를 실험하는 지점이다.
            // 이 예제의 participant는 PRE_COMMITTED 상태가 오래 유지되면 스케줄러로 commit한다.
            if (failurePoint == CancelFailurePoint.AFTER_PRE_COMMIT) {
                throw new IllegalStateException("Simulated coordinator failure after preCommit phase");
            }

            // 9. doCommit phase.
            // 모든 participant가 PRE_COMMITTED 상태이므로 실제 재고 복구와 결제 환불을 수행한다.
            inventoryClient.commitRestore(transactionId, failurePoint == CancelFailurePoint.INVENTORY_COMMIT);
            inventoryCommitted = true;
            transactionLogService.markInventoryCommitted(transactionId);

            paymentClient.commitRefund(transactionId, failurePoint == CancelFailurePoint.PAYMENT_COMMIT);
            paymentCommitted = true;
            transactionLogService.markPaymentCommitted(transactionId);

            // 10. participant commit이 모두 끝났을 때 주문도 최종 취소 완료로 바꾼다.
            orderStateService.completeCancel(orderId);
            transactionLogService.markCommitted(transactionId);
            return new CancelOrderResponse(orderId, "CANCELLED", true, true, "3PC commit completed. transactionId=" + transactionId);
        } catch (RuntimeException exception) {
            // 11. 실패가 발생하면 아직 PRE_COMMITTED가 되지 않은 participant만 rollback한다.
            // PRE_COMMITTED 이후에는 participant가 자율 commit할 수 있으므로 단순 rollback 대상으로 보지 않는다.
            rollbackPreparedParticipants(
                    transactionId,
                    inventoryPrepared,
                    paymentPrepared,
                    inventoryPreCommitted,
                    paymentPreCommitted,
                    inventoryCommitted,
                    paymentCommitted
            );

            // 12. PRE_COMMITTED 또는 COMMITTED participant가 있으면 복구/관찰 대상이다.
            // 3PC는 blocking을 줄이려는 프로토콜이지만, 네트워크 분리와 타이밍에 따라 애매한 상태는 여전히 관찰될 수 있다.
            if (inventoryPreCommitted || paymentPreCommitted || inventoryCommitted || paymentCommitted) {
                transactionLogService.markFailed(transactionId, exception.getMessage());
            } else {
                transactionLogService.markRolledBack(transactionId, exception.getMessage());
            }

            // 13. 주문은 취소 실패로 표시하고, 응답에는 실제 commit된 participant 여부를 담는다.
            orderStateService.failCancel(orderId);
            return new CancelOrderResponse(
                    orderId,
                    "CANCEL_FAILED",
                    inventoryCommitted,
                    paymentCommitted,
                    "3PC flow failed. transactionId=%s, reason=%s".formatted(transactionId, exception.getMessage())
            );
        }
    }

    private void rollbackPreparedParticipants(
            String transactionId,
            boolean inventoryPrepared,
            boolean paymentPrepared,
            boolean inventoryPreCommitted,
            boolean paymentPreCommitted,
            boolean inventoryCommitted,
            boolean paymentCommitted
    ) {
        // PREPARED까지만 간 작업은 아직 실제 재고/환불을 반영하지 않았으므로 rollback 가능하다.
        // PRE_COMMITTED 이후 작업은 participant가 commit 방향으로 자율 결정할 수 있어 여기서 rollback하지 않는다.
        if (paymentPrepared && !paymentPreCommitted && !paymentCommitted) {
            paymentClient.rollbackRefund(transactionId);
        }
        if (inventoryPrepared && !inventoryPreCommitted && !inventoryCommitted) {
            inventoryClient.rollbackRestore(transactionId);
        }
    }
}
