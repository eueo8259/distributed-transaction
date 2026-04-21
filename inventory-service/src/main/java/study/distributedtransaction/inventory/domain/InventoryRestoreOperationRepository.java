package study.distributedtransaction.inventory.domain;

import org.springframework.data.jpa.repository.JpaRepository;

public interface InventoryRestoreOperationRepository extends JpaRepository<InventoryRestoreOperation, String> {
}
