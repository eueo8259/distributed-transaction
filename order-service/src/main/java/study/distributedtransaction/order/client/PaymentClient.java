package study.distributedtransaction.order.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import study.distributedtransaction.common.PaymentRefundRequest;
import study.distributedtransaction.common.PaymentRefundResponse;

@Component
public class PaymentClient {

    private final RestClient restClient;

    public PaymentClient(
            RestClient.Builder builder,
            @Value("${services.payment.base-url}") String paymentBaseUrl
    ) {
        this.restClient = builder.baseUrl(paymentBaseUrl).build();
    }

    public PaymentRefundResponse refund(PaymentRefundRequest request, boolean fail) {
        return restClient.post()
                .uri(uriBuilder -> uriBuilder.path("/payments/refund")
                        .queryParam("fail", fail)
                        .build())
                .body(request)
                .retrieve()
                .body(PaymentRefundResponse.class);
    }
}
