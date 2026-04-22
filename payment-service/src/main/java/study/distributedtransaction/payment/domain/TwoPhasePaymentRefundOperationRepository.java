package study.distributedtransaction.payment.domain;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TwoPhasePaymentRefundOperationRepository extends JpaRepository<TwoPhasePaymentRefundOperation, String> {
}
