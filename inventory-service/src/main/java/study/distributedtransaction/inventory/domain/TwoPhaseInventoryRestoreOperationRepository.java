package study.distributedtransaction.inventory.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface TwoPhaseInventoryRestoreOperationRepository extends JpaRepository<TwoPhaseInventoryRestoreOperation, String> {

    List<TwoPhaseInventoryRestoreOperation> findByStatusAndPreCommittedAtBefore(
            TwoPhaseInventoryStatus status,
            Instant preCommittedAt
    );
}
