package study.distributedtransaction.inventory.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import study.distributedtransaction.common.InventoryCompensatedEvent;
import study.distributedtransaction.common.InventoryRestoreFailedEvent;

/**
 * inventory-service가 처리 결과 이벤트를 order-service로 다시 전달한다.
 * order-service는 이 이벤트를 받아 주문의 최종 상태를 갱신한다.
 */
@Component
public class OrderEventClient {

    private final RestClient restClient;

    public OrderEventClient(
            RestClient.Builder builder,
            @Value("${services.order.base-url}") String orderBaseUrl
    ) {
        this.restClient = builder.baseUrl(orderBaseUrl).build();
    }

    public void publishInventoryRestoreFailed(InventoryRestoreFailedEvent event) {
        restClient.post()
                .uri("/internal/events/inventory-restore-failed")
                .body(event)
                .retrieve()
                .toBodilessEntity();
    }

    public void publishInventoryCompensated(InventoryCompensatedEvent event) {
        restClient.post()
                .uri("/internal/events/inventory-compensated")
                .body(event)
                .retrieve()
                .toBodilessEntity();
    }
}
