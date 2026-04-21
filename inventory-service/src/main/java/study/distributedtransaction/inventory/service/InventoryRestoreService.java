package study.distributedtransaction.inventory.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import study.distributedtransaction.common.InventoryRestoreRequest;
import study.distributedtransaction.common.InventoryRestoreResponse;
import study.distributedtransaction.inventory.domain.InventoryItem;
import study.distributedtransaction.inventory.domain.InventoryItemRepository;
import study.distributedtransaction.inventory.domain.InventoryRestoreOperation;
import study.distributedtransaction.inventory.domain.InventoryRestoreOperationRepository;

@Service
public class InventoryRestoreService {

    private final InventoryItemRepository itemRepository;
    private final InventoryRestoreOperationRepository operationRepository;

    public InventoryRestoreService(
            InventoryItemRepository itemRepository,
            InventoryRestoreOperationRepository operationRepository
    ) {
        this.itemRepository = itemRepository;
        this.operationRepository = operationRepository;
    }

    @Transactional
    public InventoryRestoreResponse restore(InventoryRestoreRequest request, boolean fail) {
        if (fail) {
            throw new IllegalStateException("Simulated inventory failure");
        }

        InventoryItem item = itemRepository.findById(request.sku())
                .orElseThrow(() -> new IllegalArgumentException("Inventory item not found. sku=" + request.sku()));

        if (operationRepository.existsById(request.idempotencyKey())) {
            return new InventoryRestoreResponse(
                    request.orderId(),
                    item.getSku(),
                    0,
                    item.getStock(),
                    "ALREADY_RESTORED"
            );
        }

        item.restore(request.quantity());
        operationRepository.save(new InventoryRestoreOperation(
                request.idempotencyKey(),
                request.orderId(),
                request.sku(),
                request.quantity()
        ));

        return new InventoryRestoreResponse(
                request.orderId(),
                item.getSku(),
                request.quantity(),
                item.getStock(),
                "RESTORED"
        );
    }
}
