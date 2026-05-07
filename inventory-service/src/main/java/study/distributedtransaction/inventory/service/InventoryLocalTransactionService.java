package study.distributedtransaction.inventory.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import study.distributedtransaction.common.OrderCancelRequestedEvent;
import study.distributedtransaction.common.PaymentRefundFailedEvent;
import study.distributedtransaction.inventory.domain.InventoryCompensationOperation;
import study.distributedtransaction.inventory.domain.InventoryCompensationOperationRepository;
import study.distributedtransaction.inventory.domain.InventoryItem;
import study.distributedtransaction.inventory.domain.InventoryItemRepository;
import study.distributedtransaction.inventory.domain.InventoryRestoreOperation;
import study.distributedtransaction.inventory.domain.InventoryRestoreOperationRepository;

/**
 * inventory-service 안에서 실제 DB 변경을 담당하는 로컬 트랜잭션 서비스다.
 *
 * 이 클래스로 분리한 이유:
 * - @Transactional 메서드를 같은 클래스 내부에서 직접 호출하면 프록시를 거치지 않아
 *   트랜잭션이 제대로 적용되지 않을 수 있다.
 * - 따라서 Saga 흐름 제어와 로컬 트랜잭션을 서로 다른 빈으로 분리해
 *   Spring 프록시 기반 트랜잭션이 확실하게 동작하도록 만든다.
 */
@Service
public class InventoryLocalTransactionService {

    private final InventoryItemRepository itemRepository;
    private final InventoryRestoreOperationRepository restoreOperationRepository;
    private final InventoryCompensationOperationRepository compensationOperationRepository;

    public InventoryLocalTransactionService(
            InventoryItemRepository itemRepository,
            InventoryRestoreOperationRepository restoreOperationRepository,
            InventoryCompensationOperationRepository compensationOperationRepository
    ) {
        this.itemRepository = itemRepository;
        this.restoreOperationRepository = restoreOperationRepository;
        this.compensationOperationRepository = compensationOperationRepository;
    }

    @Transactional
    public void restoreInventory(OrderCancelRequestedEvent event) {
        // inventory-service 내부에서만 열리는 로컬 트랜잭션이다.
        // 메서드 시작 시 트랜잭션이 열리고, 정상 종료 시 커밋된다.
        if ("INVENTORY_RESTORE".equalsIgnoreCase(event.failurePoint())) {
            throw new IllegalStateException("Simulated inventory restore failure");
        }

        InventoryItem item = itemRepository.findById(event.sku())
                .orElseThrow(() -> new IllegalArgumentException("Inventory item not found. sku=" + event.sku()));

        if (restoreOperationRepository.existsById(event.sagaId())) {
            return;
        }

        item.restore(event.quantity());
        restoreOperationRepository.save(new InventoryRestoreOperation(
                event.sagaId(),
                event.orderId(),
                event.sku(),
                event.quantity()
        ));
    }

    @Transactional
    public void compensateInventory(PaymentRefundFailedEvent event) {
        // 보상 트랜잭션은 과거 DB 트랜잭션을 되돌리는 "롤백"이 아니다.
        // 이전 재고 복구 트랜잭션은 이미 커밋되었고,
        // 지금은 새로운 로컬 트랜잭션을 열어 반대 비즈니스 동작을 수행한다.
        if ("INVENTORY_COMPENSATE".equalsIgnoreCase(event.failurePoint())) {
            throw new IllegalStateException("Simulated inventory compensation failure");
        }

        InventoryItem item = itemRepository.findById(event.sku())
                .orElseThrow(() -> new IllegalArgumentException("Inventory item not found. sku=" + event.sku()));

        if (compensationOperationRepository.existsById(event.sagaId())) {
            return;
        }

        item.deduct(event.quantity());
        compensationOperationRepository.save(new InventoryCompensationOperation(
                event.sagaId(),
                event.orderId(),
                event.sku(),
                event.quantity()
        ));
    }
}
