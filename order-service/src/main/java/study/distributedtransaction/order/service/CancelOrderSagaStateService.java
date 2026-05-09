package study.distributedtransaction.order.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import study.distributedtransaction.order.domain.CancelOrderSaga;
import study.distributedtransaction.order.domain.CancelOrderSagaRepository;

@Service
public class CancelOrderSagaStateService {

    private final CancelOrderSagaRepository sagaRepository;

    public CancelOrderSagaStateService(CancelOrderSagaRepository sagaRepository) {
        this.sagaRepository = sagaRepository;
    }

    @Transactional
    public void markInventoryRestored(String sagaId) {
        find(sagaId).markInventoryRestored();
    }

    @Transactional
    public void markPaymentRefunded(String sagaId) {
        find(sagaId).markPaymentRefunded();
    }

    @Transactional
    public void markCompleted(String sagaId) {
        find(sagaId).markCompleted();
    }

    @Transactional
    public void markInventoryCompensated(String sagaId, String reason) {
        find(sagaId).markInventoryCompensated(reason);
    }

    @Transactional
    public void markFailed(String sagaId, String reason) {
        find(sagaId).markFailed(reason);
    }

    private CancelOrderSaga find(String sagaId) {
        return sagaRepository.findById(sagaId)
                .orElseThrow(() -> new IllegalArgumentException("Cancel order saga not found. sagaId=" + sagaId));
    }
}
