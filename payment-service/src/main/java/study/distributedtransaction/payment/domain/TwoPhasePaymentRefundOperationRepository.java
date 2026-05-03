package study.distributedtransaction.payment.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface TwoPhasePaymentRefundOperationRepository extends JpaRepository<TwoPhasePaymentRefundOperation, String> {

    List<TwoPhasePaymentRefundOperation> findByStatusAndPreCommittedAtBefore(
            TwoPhasePaymentStatus status,
            Instant preCommittedAt
    );
}
