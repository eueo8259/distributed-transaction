package study.distributedtransaction.order.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import study.distributedtransaction.order.domain.TwoPhaseCancelTransaction;
import study.distributedtransaction.order.domain.TwoPhaseCancelTransactionRepository;

import java.util.List;

@Service
public class TwoPhaseCancelLogService {

    private final TwoPhaseCancelTransactionRepository transactionRepository;

    public TwoPhaseCancelLogService(TwoPhaseCancelTransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    @Transactional
    public void start(String transactionId, Long orderId) {
        transactionRepository.save(new TwoPhaseCancelTransaction(transactionId, orderId));
    }

    @Transactional
    public void markInventoryPrepared(String transactionId) {
        find(transactionId).markInventoryPrepared();
    }

    @Transactional
    public void markPaymentPrepared(String transactionId) {
        find(transactionId).markPaymentPrepared();
    }

    @Transactional
    public void markPrepared(String transactionId) {
        find(transactionId).markPrepared();
    }

    @Transactional
    public void markInventoryCommitted(String transactionId) {
        find(transactionId).markInventoryCommitted();
    }

    @Transactional
    public void markPaymentCommitted(String transactionId) {
        find(transactionId).markPaymentCommitted();
    }

    @Transactional
    public void markCommitted(String transactionId) {
        find(transactionId).markCommitted();
    }

    @Transactional
    public void markRolledBack(String transactionId, String reason) {
        find(transactionId).markRolledBack(reason);
    }

    @Transactional
    public void markFailed(String transactionId, String reason) {
        find(transactionId).markFailed(reason);
    }

    @Transactional(readOnly = true)
    public List<TwoPhaseCancelTransaction> findAll() {
        return transactionRepository.findAll();
    }

    private TwoPhaseCancelTransaction find(String transactionId) {
        return transactionRepository.findById(transactionId)
                .orElseThrow(() -> new IllegalArgumentException("2PC transaction not found. transactionId=" + transactionId));
    }
}
