package study.distributedtransaction.order.domain;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CancelOrderSagaRepository extends JpaRepository<CancelOrderSaga, String> {
}
