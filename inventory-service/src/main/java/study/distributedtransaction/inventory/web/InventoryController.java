package study.distributedtransaction.inventory.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import study.distributedtransaction.common.InventoryDeductRequest;
import study.distributedtransaction.common.InventoryDeductResponse;
import study.distributedtransaction.common.InventoryRestoreRequest;
import study.distributedtransaction.common.InventoryRestoreResponse;
import study.distributedtransaction.inventory.domain.InventoryDeductOperation;
import study.distributedtransaction.inventory.domain.InventoryDeductOperationRepository;
import study.distributedtransaction.inventory.domain.InventoryItem;
import study.distributedtransaction.inventory.domain.InventoryItemRepository;
import study.distributedtransaction.inventory.domain.InventoryRestoreOperation;
import study.distributedtransaction.inventory.domain.InventoryRestoreOperationRepository;
import study.distributedtransaction.inventory.service.InventoryDeductService;
import study.distributedtransaction.inventory.service.InventoryRestoreService;

import java.util.List;

@RestController
public class InventoryController {

    private final InventoryItemRepository itemRepository;
    private final InventoryRestoreOperationRepository restoreOperationRepository;
    private final InventoryDeductOperationRepository deductOperationRepository;
    private final InventoryRestoreService restoreService;
    private final InventoryDeductService deductService;

    public InventoryController(
            InventoryItemRepository itemRepository,
            InventoryRestoreOperationRepository restoreOperationRepository,
            InventoryDeductOperationRepository deductOperationRepository,
            InventoryRestoreService restoreService,
            InventoryDeductService deductService
    ) {
        this.itemRepository = itemRepository;
        this.restoreOperationRepository = restoreOperationRepository;
        this.deductOperationRepository = deductOperationRepository;
        this.restoreService = restoreService;
        this.deductService = deductService;
    }

    @GetMapping("/inventory")
    public List<InventoryItem> findAll() {
        return itemRepository.findAll();
    }

    @PostMapping("/inventory/restore")
    public InventoryRestoreResponse restore(
            @RequestBody InventoryRestoreRequest request,
            @RequestParam(defaultValue = "false") boolean fail
    ) {
        return restoreService.restore(request, fail);
    }

    @PostMapping("/inventory/deduct")
    public InventoryDeductResponse deduct(
            @RequestBody InventoryDeductRequest request,
            @RequestParam(defaultValue = "false") boolean fail
    ) {
        return deductService.deduct(request, fail);
    }

    @GetMapping("/inventory/restore-operations")
    public List<InventoryRestoreOperation> findRestoreOperations() {
        return restoreOperationRepository.findAll();
    }

    @GetMapping("/inventory/deduct-operations")
    public List<InventoryDeductOperation> findDeductOperations() {
        return deductOperationRepository.findAll();
    }
}
