package study.distributedtransaction.payment.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import study.distributedtransaction.common.PaymentRefundedEvent;

/**
 * payment-service는 환불 성공 결과를 order-service로 전달한다.
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

    public void publishPaymentRefunded(PaymentRefundedEvent event) {
        restClient.post()
                .uri("/internal/events/payment-refunded")
                .body(event)
                .retrieve()
                .toBodilessEntity();
    }
}
