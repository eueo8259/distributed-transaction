package study.distributedtransaction.payment.domain;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRefundOperationRepository extends JpaRepository<PaymentRefundOperation, String> {
}
