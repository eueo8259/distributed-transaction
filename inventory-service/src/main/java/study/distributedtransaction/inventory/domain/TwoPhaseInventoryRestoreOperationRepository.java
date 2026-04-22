package study.distributedtransaction.inventory.domain;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TwoPhaseInventoryRestoreOperationRepository extends JpaRepository<TwoPhaseInventoryRestoreOperation, String> {
}
