package study.distributedtransaction.inventory.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import study.distributedtransaction.common.InventoryRestoredEvent;

/**
 * inventory-service는 payment-service의 비즈니스 메서드를 직접 호출하지 않는다.
 * 대신 payment-service가 구독할 이벤트를 발행한다.
 */
@Component
public class PaymentEventClient {

    private final RestClient restClient;

    public PaymentEventClient(
            RestClient.Builder builder,
            @Value("${services.payment.base-url}") String paymentBaseUrl
    ) {
        this.restClient = builder.baseUrl(paymentBaseUrl).build();
    }

    public void publishInventoryRestored(InventoryRestoredEvent event) {
        restClient.post()
                .uri("/internal/events/inventory-restored")
                .body(event)
                .retrieve()
                .toBodilessEntity();
    }
}
