package study.distributedtransaction.inventory.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import study.distributedtransaction.common.InventoryRestoreRequest;
import study.distributedtransaction.common.TwoPhaseResponse;
import study.distributedtransaction.inventory.domain.InventoryItem;
import study.distributedtransaction.inventory.domain.InventoryItemRepository;
import study.distributedtransaction.inventory.domain.TwoPhaseInventoryRestoreOperation;
import study.distributedtransaction.inventory.domain.TwoPhaseInventoryRestoreOperationRepository;
import study.distributedtransaction.inventory.domain.TwoPhaseInventoryStatus;

import java.time.Duration;
import java.time.Instant;

@Service
public class TwoPhaseInventoryRestoreService {

    private static final String PARTICIPANT = "inventory-service";

    private final InventoryItemRepository itemRepository;
    private final TwoPhaseInventoryRestoreOperationRepository operationRepository;
    // PRE_COMMITTED 상태가 이 시간보다 오래 유지되면 coordinator 장애로 보고 participant가 자율 commit한다.
    private final Duration preCommitTimeout;

    public TwoPhaseInventoryRestoreService(
            InventoryItemRepository itemRepository,
            TwoPhaseInventoryRestoreOperationRepository operationRepository,
            @Value("${three-phase.pre-commit-timeout-ms:30000}") long preCommitTimeoutMillis
    ) {
        this.itemRepository = itemRepository;
        this.operationRepository = operationRepository;
        this.preCommitTimeout = Duration.ofMillis(preCommitTimeoutMillis);
    }

    @Transactional
    public TwoPhaseResponse prepare(InventoryRestoreRequest request, boolean fail) {
        if (fail) {
            throw new IllegalStateException("Simulated inventory prepare failure");
        }

        // prepare 단계에서는 실제 재고 수량을 바꾸지 않는다.
        // 재고 row가 존재하는지만 검증하고, 이후 commit에서 사용할 pending operation을 저장한다.
        itemRepository.findById(request.sku())
                .orElseThrow(() -> new IllegalArgumentException("Inventory item not found. sku=" + request.sku()));

        return operationRepository.findById(request.idempotencyKey())
                .map(operation -> new TwoPhaseResponse(
                        operation.getTransactionId(),
                        PARTICIPANT,
                        operation.getStatus().name(),
                        "prepare request is idempotent"
                ))
                .orElseGet(() -> {
                    // 같은 transactionId가 다시 들어와도 중복 operation을 만들지 않기 위해 transactionId를 PK로 사용한다.
                    // 이 row가 이 예제에서 PREPARED 상태와 논리적 pending 작업을 표현한다.
                    operationRepository.save(new TwoPhaseInventoryRestoreOperation(
                            request.idempotencyKey(),
                            request.orderId(),
                            request.sku(),
                            request.quantity()
                    ));
                    return new TwoPhaseResponse(request.idempotencyKey(), PARTICIPANT, "PREPARED", "stock restore prepared");
                });
    }

    @Transactional
    public TwoPhaseResponse preCommit(String transactionId, boolean fail) {
        if (fail) {
            throw new IllegalStateException("Simulated inventory preCommit failure");
        }

        // preCommit은 3PC에서 추가된 단계다.
        // coordinator가 모든 participant의 prepare 성공을 확인했고, 이제 commit 방향으로 진행하겠다고 알리는 신호다.
        TwoPhaseInventoryRestoreOperation operation = find(transactionId);
        if (operation.getStatus() == TwoPhaseInventoryStatus.PRE_COMMITTED) {
            return new TwoPhaseResponse(transactionId, PARTICIPANT, "PRE_COMMITTED", "preCommit request is idempotent");
        }
        if (operation.getStatus() == TwoPhaseInventoryStatus.COMMITTED) {
            return new TwoPhaseResponse(transactionId, PARTICIPANT, "COMMITTED", "already committed");
        }
        if (operation.getStatus() == TwoPhaseInventoryStatus.ROLLED_BACK) {
            throw new IllegalStateException("Cannot preCommit a rolled back inventory operation. transactionId=" + transactionId);
        }

        // 아직 실제 재고를 복구하지는 않고, participant가 자율 commit할 수 있는 PRE_COMMITTED 상태만 기록한다.
        operation.preCommit();
        return new TwoPhaseResponse(transactionId, PARTICIPANT, "PRE_COMMITTED", "stock restore pre-committed");
    }

    @Transactional
    public TwoPhaseResponse commit(String transactionId, boolean fail) {
        if (fail) {
            throw new IllegalStateException("Simulated inventory commit failure");
        }

        TwoPhaseInventoryRestoreOperation operation = find(transactionId);
        if (operation.getStatus() == TwoPhaseInventoryStatus.COMMITTED) {
            return new TwoPhaseResponse(transactionId, PARTICIPANT, "COMMITTED", "commit request is idempotent");
        }
        if (operation.getStatus() == TwoPhaseInventoryStatus.ROLLED_BACK) {
            throw new IllegalStateException("Cannot commit a rolled back inventory operation. transactionId=" + transactionId);
        }
        // 3PC에서는 commit 전에 반드시 preCommit을 거쳤는지 확인한다.
        // 이 체크가 없으면 2PC처럼 PREPARED 상태에서 바로 commit할 수 있어 3PC 흐름을 관찰하기 어렵다.
        if (operation.getStatus() != TwoPhaseInventoryStatus.PRE_COMMITTED) {
            throw new IllegalStateException("3PC commit requires PRE_COMMITTED inventory operation. transactionId=" + transactionId);
        }

        applyCommit(operation);
        return new TwoPhaseResponse(transactionId, PARTICIPANT, "COMMITTED", "stock restored");
    }

    @Transactional
    public TwoPhaseResponse rollback(String transactionId) {
        TwoPhaseInventoryRestoreOperation operation = find(transactionId);
        if (operation.getStatus() == TwoPhaseInventoryStatus.COMMITTED) {
            return new TwoPhaseResponse(transactionId, PARTICIPANT, "COMMITTED", "already committed; 3PC cannot compensate here");
        }
        if (operation.getStatus() == TwoPhaseInventoryStatus.ROLLED_BACK) {
            return new TwoPhaseResponse(transactionId, PARTICIPANT, "ROLLED_BACK", "rollback request is idempotent");
        }
        // PRE_COMMITTED 상태는 coordinator가 commit 방향을 이미 알린 상태다.
        // 이 예제에서는 3PC의 자율 결정 개념을 보여주기 위해 rollback을 거부하고 timeout commit 대상으로 둔다.
        if (operation.getStatus() == TwoPhaseInventoryStatus.PRE_COMMITTED) {
            throw new IllegalStateException("Cannot rollback a pre-committed inventory operation. transactionId=" + transactionId);
        }

        operation.rollback();
        return new TwoPhaseResponse(transactionId, PARTICIPANT, "ROLLED_BACK", "prepared stock restore discarded");
    }

    @Scheduled(fixedDelayString = "${three-phase.recovery-interval-ms:5000}")
    @Transactional
    public void autoCommitTimedOutPreCommits() {
        // 3PC의 핵심 실험 지점이다.
        // coordinator가 preCommit까지 보낸 뒤 사라졌다고 가정하면 participant는 영원히 기다리지 않고 timeout 후 commit한다.
        Instant deadline = Instant.now().minus(preCommitTimeout);
        operationRepository.findByStatusAndPreCommittedAtBefore(TwoPhaseInventoryStatus.PRE_COMMITTED, deadline)
                .forEach(this::applyCommit);
    }

    private void applyCommit(TwoPhaseInventoryRestoreOperation operation) {
        // 실제 재고 변경은 commit 단계에서만 수행한다.
        // prepare/preCommit은 상태 기록이고, 여기서 처음으로 inventory row가 바뀐다.
        InventoryItem item = itemRepository.findById(operation.getSku())
                .orElseThrow(() -> new IllegalArgumentException("Inventory item not found. sku=" + operation.getSku()));
        item.restore(operation.getQuantity());
        operation.commit();
    }

    private TwoPhaseInventoryRestoreOperation find(String transactionId) {
        return operationRepository.findById(transactionId)
                .orElseThrow(() -> new IllegalArgumentException("Inventory 3PC operation not found. transactionId=" + transactionId));
    }
}
