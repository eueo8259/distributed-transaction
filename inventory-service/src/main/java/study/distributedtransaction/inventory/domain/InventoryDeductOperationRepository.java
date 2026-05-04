package study.distributedtransaction.inventory.domain;

import org.springframework.data.jpa.repository.JpaRepository;

public interface InventoryDeductOperationRepository extends JpaRepository<InventoryDeductOperation, String> {
}
