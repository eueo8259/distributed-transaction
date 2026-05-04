package study.distributedtransaction.inventory.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import study.distributedtransaction.common.InventoryDeductRequest;
import study.distributedtransaction.common.InventoryDeductResponse;
import study.distributedtransaction.inventory.domain.InventoryDeductOperation;
import study.distributedtransaction.inventory.domain.InventoryDeductOperationRepository;
import study.distributedtransaction.inventory.domain.InventoryItem;
import study.distributedtransaction.inventory.domain.InventoryItemRepository;

@Service
public class InventoryDeductService {

    private final InventoryItemRepository itemRepository;
    private final InventoryDeductOperationRepository operationRepository;

    public InventoryDeductService(
            InventoryItemRepository itemRepository,
            InventoryDeductOperationRepository operationRepository
    ) {
        this.itemRepository = itemRepository;
        this.operationRepository = operationRepository;
    }

    @Transactional
    public InventoryDeductResponse deduct(InventoryDeductRequest request, boolean fail) {
        if (fail) {
            throw new IllegalStateException("Simulated inventory compensation failure");
        }

        InventoryItem item = itemRepository.findById(request.sku())
                .orElseThrow(() -> new IllegalArgumentException("Inventory item not found. sku=" + request.sku()));

        if (operationRepository.existsById(request.idempotencyKey())) {
            return new InventoryDeductResponse(
                    request.orderId(),
                    item.getSku(),
                    0,
                    item.getStock(),
                    "ALREADY_DEDUCTED"
            );
        }

        item.deduct(request.quantity());
        operationRepository.save(new InventoryDeductOperation(
                request.idempotencyKey(),
                request.orderId(),
                request.sku(),
                request.quantity()
        ));

        return new InventoryDeductResponse(
                request.orderId(),
                item.getSku(),
                request.quantity(),
                item.getStock(),
                "DEDUCTED"
        );
    }
}
