package study.distributedtransaction.order.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import study.distributedtransaction.common.InventoryRestoreRequest;
import study.distributedtransaction.common.InventoryRestoreResponse;

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
}
