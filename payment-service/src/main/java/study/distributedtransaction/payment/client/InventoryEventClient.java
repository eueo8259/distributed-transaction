package study.distributedtransaction.payment.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import study.distributedtransaction.common.PaymentRefundFailedEvent;

/**
 * payment-service는 환불 실패 이벤트를 inventory-service로 전달해 보상 트랜잭션을 시작하게 한다.
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

    public void publishPaymentRefundFailed(PaymentRefundFailedEvent event) {
        restClient.post()
                .uri("/internal/events/payment-refund-failed")
                .body(event)
                .retrieve()
                .toBodilessEntity();
    }
}
