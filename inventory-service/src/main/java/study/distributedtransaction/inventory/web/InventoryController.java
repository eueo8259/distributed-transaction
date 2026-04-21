package study.distributedtransaction.inventory.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import study.distributedtransaction.common.InventoryRestoreRequest;
import study.distributedtransaction.common.InventoryRestoreResponse;
import study.distributedtransaction.inventory.domain.InventoryItem;
import study.distributedtransaction.inventory.domain.InventoryItemRepository;
import study.distributedtransaction.inventory.service.InventoryRestoreService;

import java.util.List;

@RestController
public class InventoryController {

    private final InventoryItemRepository itemRepository;
    private final InventoryRestoreService restoreService;

    public InventoryController(InventoryItemRepository itemRepository, InventoryRestoreService restoreService) {
        this.itemRepository = itemRepository;
        this.restoreService = restoreService;
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
}
