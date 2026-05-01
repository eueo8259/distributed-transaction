package study.distributedtransaction.inventory.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import study.distributedtransaction.common.InventoryRestoreRequest;
import study.distributedtransaction.common.TwoPhaseResponse;
import study.distributedtransaction.inventory.domain.InventoryItem;
import study.distributedtransaction.inventory.domain.InventoryItemRepository;
import study.distributedtransaction.inventory.domain.TwoPhaseInventoryRestoreOperation;
import study.distributedtransaction.inventory.domain.TwoPhaseInventoryRestoreOperationRepository;
import study.distributedtransaction.inventory.domain.TwoPhaseInventoryStatus;

@Service
public class TwoPhaseInventoryRestoreService {

    private static final String PARTICIPANT = "inventory-service";

    private final InventoryItemRepository itemRepository;
    private final TwoPhaseInventoryRestoreOperationRepository operationRepository;

    public TwoPhaseInventoryRestoreService(
            InventoryItemRepository itemRepository,
            TwoPhaseInventoryRestoreOperationRepository operationRepository
    ) {
        this.itemRepository = itemRepository;
        this.operationRepository = operationRepository;
    }

    @Transactional
    public TwoPhaseResponse prepare(InventoryRestoreRequest request, boolean fail) {
        if (fail) {
            throw new IllegalStateException("Simulated inventory prepare failure");
        }

        // 현재 예제의 prepare는 짧은 로컬 트랜잭션으로 재고 존재 여부만 확인하고 pending operation을 저장한다.
        // 따라서 이 메서드가 반환된 뒤 H2의 실제 row lock은 유지되지 않는다.
        // 다만 실제 XA 기반 2PC라면 prepare 시점에 잡은 재고 row lock이 coordinator의 commit/rollback 결정까지 유지될 수 있다.
        itemRepository.findById(request.sku())
                .orElseThrow(() -> new IllegalArgumentException("Inventory item not found. sku=" + request.sku()));

        return operationRepository.findById(request.idempotencyKey())
                .map(operation -> new TwoPhaseResponse(
                        operation.getTransactionId(),
                        PARTICIPANT,
                        operation.getStatus().name(),
                        "prepare request is idempotent"
                ))
                .orElseGet(() -> {
                    // pending operation은 이 예제에서 "논리적 락" 역할을 한다.
                    // 같은 transactionId가 다시 들어오면 중복 재고 복구를 막고, commit/rollback 전 상태를 관찰할 수 있다.
                    operationRepository.save(new TwoPhaseInventoryRestoreOperation(
                            request.idempotencyKey(),
                            request.orderId(),
                            request.sku(),
                            request.quantity()
                    ));
                    return new TwoPhaseResponse(request.idempotencyKey(), PARTICIPANT, "PREPARED", "stock restore prepared");
                });
    }

    @Transactional
    public TwoPhaseResponse commit(String transactionId, boolean fail) {
        if (fail) {
            throw new IllegalStateException("Simulated inventory commit failure");
        }

        TwoPhaseInventoryRestoreOperation operation = find(transactionId);
        if (operation.getStatus() == TwoPhaseInventoryStatus.COMMITTED) {
            return new TwoPhaseResponse(transactionId, PARTICIPANT, "COMMITTED", "commit request is idempotent");
        }
        if (operation.getStatus() == TwoPhaseInventoryStatus.ROLLED_BACK) {
            throw new IllegalStateException("Cannot commit a rolled back inventory operation. transactionId=" + transactionId);
        }

        InventoryItem item = itemRepository.findById(operation.getSku())
                .orElseThrow(() -> new IllegalArgumentException("Inventory item not found. sku=" + operation.getSku()));
        // commit의 로컬 트랜잭션 안에서 실제 재고 row를 변경하고 operation 상태를 COMMITTED로 바꾼다.
        // 이 짧은 구간에는 일반적인 DB write lock이 걸리지만, 메서드가 끝나면 해제된다.
        item.restore(operation.getQuantity());
        operation.commit();

        return new TwoPhaseResponse(transactionId, PARTICIPANT, "COMMITTED", "stock restored");
    }

    @Transactional
    public TwoPhaseResponse rollback(String transactionId) {
        TwoPhaseInventoryRestoreOperation operation = find(transactionId);
        if (operation.getStatus() == TwoPhaseInventoryStatus.COMMITTED) {
            return new TwoPhaseResponse(transactionId, PARTICIPANT, "COMMITTED", "already committed; 2PC cannot compensate here");
        }
        if (operation.getStatus() == TwoPhaseInventoryStatus.ROLLED_BACK) {
            return new TwoPhaseResponse(transactionId, PARTICIPANT, "ROLLED_BACK", "rollback request is idempotent");
        }

        // prepare에서 실제 재고를 바꾸지 않았기 때문에 rollback은 pending operation만 ROLLED_BACK으로 바꾼다.
        // 실제 XA 2PC에서는 이 결정이 와야 prepare 상태에서 잡고 있던 DB lock을 풀 수 있다.
        operation.rollback();
        return new TwoPhaseResponse(transactionId, PARTICIPANT, "ROLLED_BACK", "prepared stock restore discarded");
    }

    private TwoPhaseInventoryRestoreOperation find(String transactionId) {
        return operationRepository.findById(transactionId)
                .orElseThrow(() -> new IllegalArgumentException("Inventory 2PC operation not found. transactionId=" + transactionId));
    }
}
