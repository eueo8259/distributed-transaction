package study.distributedtransaction.inventory.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import study.distributedtransaction.inventory.domain.InventoryItem;
import study.distributedtransaction.inventory.domain.InventoryItemRepository;

@Component
public class InventorySeedData implements ApplicationRunner {

    private final InventoryItemRepository itemRepository;

    public InventorySeedData(InventoryItemRepository itemRepository) {
        this.itemRepository = itemRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (itemRepository.count() == 0) {
            itemRepository.save(new InventoryItem("SKU-001", 8));
        }
    }
}
