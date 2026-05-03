package study.distributedtransaction.inventory.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import study.distributedtransaction.common.InventoryRestoreRequest;
import study.distributedtransaction.common.TwoPhaseDecisionRequest;
import study.distributedtransaction.common.TwoPhaseResponse;
import study.distributedtransaction.inventory.domain.TwoPhaseInventoryRestoreOperation;
import study.distributedtransaction.inventory.domain.TwoPhaseInventoryRestoreOperationRepository;
import study.distributedtransaction.inventory.service.TwoPhaseInventoryRestoreService;

import java.util.List;

@RestController
public class TwoPhaseInventoryController {

    private final TwoPhaseInventoryRestoreService restoreService;
    private final TwoPhaseInventoryRestoreOperationRepository operationRepository;

    public TwoPhaseInventoryController(
            TwoPhaseInventoryRestoreService restoreService,
            TwoPhaseInventoryRestoreOperationRepository operationRepository
    ) {
        this.restoreService = restoreService;
        this.operationRepository = operationRepository;
    }

    @PostMapping("/inventory/3pc/prepare")
    public TwoPhaseResponse prepare(
            @RequestBody InventoryRestoreRequest request,
            @RequestParam(defaultValue = "false") boolean fail
    ) {
        return restoreService.prepare(request, fail);
    }

    @PostMapping("/inventory/3pc/pre-commit")
    public TwoPhaseResponse preCommit(
            @RequestBody TwoPhaseDecisionRequest request,
            @RequestParam(defaultValue = "false") boolean fail
    ) {
        return restoreService.preCommit(request.transactionId(), fail);
    }

    @PostMapping("/inventory/3pc/commit")
    public TwoPhaseResponse commit(
            @RequestBody TwoPhaseDecisionRequest request,
            @RequestParam(defaultValue = "false") boolean fail
    ) {
        return restoreService.commit(request.transactionId(), fail);
    }

    @PostMapping("/inventory/3pc/rollback")
    public TwoPhaseResponse rollback(@RequestBody TwoPhaseDecisionRequest request) {
        return restoreService.rollback(request.transactionId());
    }

    @GetMapping("/inventory/3pc/operations")
    public List<TwoPhaseInventoryRestoreOperation> findOperations() {
        return operationRepository.findAll();
    }
}
