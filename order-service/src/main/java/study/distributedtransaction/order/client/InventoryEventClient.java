package study.distributedtransaction.order.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import study.distributedtransaction.common.OrderCancelRequestedEvent;

/**
 * order-service가 inventory-service로 이벤트를 보내며 Choreography를 시작한다.
 * 실무에서는 Kafka/RabbitMQ 같은 메시지 브로커를 많이 쓰지만,
 * 여기서는 학습을 쉽게 하기 위해 HTTP 호출로 이벤트 전달을 흉내 낸다.
 */
@Component
public class InventoryEventClient {

    private final RestClient restClient;

    public InventoryEventClient(
            RestClient.Builder builder,
            @Value("${services.inventory.base-url}") String inventoryBaseUrl
    ) {
        this.restClient = builder.baseUrl(inventoryBaseUrl).build();
    }

    public void publishOrderCancelRequested(OrderCancelRequestedEvent event) {
        restClient.post()
                .uri("/internal/events/order-cancel-requested")
                .body(event)
                .retrieve()
                .toBodilessEntity();
    }
}
