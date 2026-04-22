package study.distributedtransaction.order.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import study.distributedtransaction.common.PaymentRefundRequest;
import study.distributedtransaction.common.PaymentRefundResponse;
import study.distributedtransaction.common.TwoPhaseDecisionRequest;
import study.distributedtransaction.common.TwoPhaseResponse;

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

    public TwoPhaseResponse prepareRefund(PaymentRefundRequest request, boolean fail) {
        return restClient.post()
                .uri(uriBuilder -> uriBuilder.path("/payments/2pc/prepare")
                        .queryParam("fail", fail)
                        .build())
                .body(request)
                .retrieve()
                .body(TwoPhaseResponse.class);
    }

    public TwoPhaseResponse commitRefund(String transactionId, boolean fail) {
        return restClient.post()
                .uri(uriBuilder -> uriBuilder.path("/payments/2pc/commit")
                        .queryParam("fail", fail)
                        .build())
                .body(new TwoPhaseDecisionRequest(transactionId))
                .retrieve()
                .body(TwoPhaseResponse.class);
    }

    public TwoPhaseResponse rollbackRefund(String transactionId) {
        return restClient.post()
                .uri("/payments/2pc/rollback")
                .body(new TwoPhaseDecisionRequest(transactionId))
                .retrieve()
                .body(TwoPhaseResponse.class);
    }
}
