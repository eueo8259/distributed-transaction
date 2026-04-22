package study.distributedtransaction.order.domain;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TwoPhaseCancelTransactionRepository extends JpaRepository<TwoPhaseCancelTransaction, String> {
}
