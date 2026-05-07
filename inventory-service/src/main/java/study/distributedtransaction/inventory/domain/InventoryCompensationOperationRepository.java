package study.distributedtransaction.inventory.domain;

import org.springframework.data.jpa.repository.JpaRepository;

public interface InventoryCompensationOperationRepository extends JpaRepository<InventoryCompensationOperation, String> {
}
