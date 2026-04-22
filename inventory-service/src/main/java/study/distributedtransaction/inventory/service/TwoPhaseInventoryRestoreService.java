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

        operation.rollback();
        return new TwoPhaseResponse(transactionId, PARTICIPANT, "ROLLED_BACK", "prepared stock restore discarded");
    }

    private TwoPhaseInventoryRestoreOperation find(String transactionId) {
        return operationRepository.findById(transactionId)
                .orElseThrow(() -> new IllegalArgumentException("Inventory 2PC operation not found. transactionId=" + transactionId));
    }
}
