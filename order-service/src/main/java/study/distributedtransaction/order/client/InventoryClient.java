package study.distributedtransaction.order.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import study.distributedtransaction.common.InventoryRestoreRequest;
import study.distributedtransaction.common.InventoryRestoreResponse;
import study.distributedtransaction.common.TwoPhaseDecisionRequest;
import study.distributedtransaction.common.TwoPhaseResponse;

@Component
public class InventoryClient {

    private final RestClient restClient;

    public InventoryClient(
            RestClient.Builder builder,
            @Value("${services.inventory.base-url}") String inventoryBaseUrl
    ) {
        this.restClient = builder.baseUrl(inventoryBaseUrl).build();
    }

    public InventoryRestoreResponse restore(InventoryRestoreRequest request, boolean fail) {
        return restClient.post()
                .uri(uriBuilder -> uriBuilder.path("/inventory/restore")
                        .queryParam("fail", fail)
                        .build())
                .body(request)
                .retrieve()
                .body(InventoryRestoreResponse.class);
    }

    public TwoPhaseResponse prepareRestore(InventoryRestoreRequest request, boolean fail) {
        return restClient.post()
                .uri(uriBuilder -> uriBuilder.path("/inventory/2pc/prepare")
                        .queryParam("fail", fail)
                        .build())
                .body(request)
                .retrieve()
                .body(TwoPhaseResponse.class);
    }

    public TwoPhaseResponse commitRestore(String transactionId, boolean fail) {
        return restClient.post()
                .uri(uriBuilder -> uriBuilder.path("/inventory/2pc/commit")
                        .queryParam("fail", fail)
                        .build())
                .body(new TwoPhaseDecisionRequest(transactionId))
                .retrieve()
                .body(TwoPhaseResponse.class);
    }

    public TwoPhaseResponse rollbackRestore(String transactionId) {
        return restClient.post()
                .uri("/inventory/2pc/rollback")
                .body(new TwoPhaseDecisionRequest(transactionId))
                .retrieve()
                .body(TwoPhaseResponse.class);
    }
}
