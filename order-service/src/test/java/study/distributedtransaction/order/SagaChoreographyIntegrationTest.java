package study.distributedtransaction.order;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.web.client.RestClient;
import study.distributedtransaction.common.CancelOrderResponse;
import study.distributedtransaction.inventory.InventoryServiceApplication;
import study.distributedtransaction.inventory.domain.InventoryCompensationOperationRepository;
import study.distributedtransaction.inventory.domain.InventoryItemRepository;
import study.distributedtransaction.inventory.domain.InventoryRestoreOperationRepository;
import study.distributedtransaction.order.domain.OrderStatus;
import study.distributedtransaction.order.domain.PurchaseOrder;
import study.distributedtransaction.order.domain.PurchaseOrderRepository;
import study.distributedtransaction.order.service.CancelFailurePoint;
import study.distributedtransaction.payment.PaymentServiceApplication;
import study.distributedtransaction.payment.domain.Payment;
import study.distributedtransaction.payment.domain.PaymentRefundOperationRepository;
import study.distributedtransaction.payment.domain.PaymentRepository;
import study.distributedtransaction.payment.domain.PaymentStatus;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.ServerSocket;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

class SagaChoreographyIntegrationTest {

    private ConfigurableApplicationContext orderContext;
    private ConfigurableApplicationContext inventoryContext;
    private ConfigurableApplicationContext paymentContext;

    @AfterEach
    void tearDown() {
        close(paymentContext);
        close(inventoryContext);
        close(orderContext);
    }

    @Test
    void 정상_취소가_완료된다() {
        startApplications();

        CancelOrderResponse response = invokeCancel(CancelFailurePoint.NONE);

        assertThat(response.status()).isEqualTo("CANCEL_REQUESTED");

        awaitUntil(() -> order().getStatus() == OrderStatus.CANCELLED);

        assertThat(order().getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(inventoryStock()).isEqualTo(10);
        assertThat(payment().getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(payment().getRefundedAmount()).isEqualByComparingTo("30000.00");
        assertThat(inventoryRestoreOperations().count()).isEqualTo(1);
        assertThat(paymentRefundOperations().count()).isEqualTo(1);
        assertThat(inventoryCompensationOperations().count()).isEqualTo(0);
    }

    @Test
    void 재고_복구가_실패하면_주문은_실패하고_다른_상태는_유지된다() {
        startApplications();

        CancelOrderResponse response = invokeCancel(CancelFailurePoint.INVENTORY_RESTORE);

        assertThat(response.status()).isEqualTo("CANCEL_REQUESTED");

        awaitUntil(() -> order().getStatus() == OrderStatus.CANCEL_FAILED);

        assertThat(order().getStatus()).isEqualTo(OrderStatus.CANCEL_FAILED);
        assertThat(inventoryStock()).isEqualTo(8);
        assertThat(payment().getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(payment().getRefundedAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(inventoryRestoreOperations().count()).isEqualTo(0);
        assertThat(paymentRefundOperations().count()).isEqualTo(0);
        assertThat(inventoryCompensationOperations().count()).isEqualTo(0);
    }

    @Test
    void 환불이_실패하면_보상_트랜잭션으로_재고를_되돌리고_주문은_실패한다() {
        startApplications();

        CancelOrderResponse response = invokeCancel(CancelFailurePoint.PAYMENT_REFUND);

        assertThat(response.status()).isEqualTo("CANCEL_REQUESTED");

        awaitUntil(() -> order().getStatus() == OrderStatus.CANCEL_FAILED);

        assertThat(order().getStatus()).isEqualTo(OrderStatus.CANCEL_FAILED);
        assertThat(inventoryStock()).isEqualTo(8);
        assertThat(payment().getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(payment().getRefundedAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(inventoryRestoreOperations().count()).isEqualTo(1);
        assertThat(paymentRefundOperations().count()).isEqualTo(0);
        assertThat(inventoryCompensationOperations().count()).isEqualTo(1);
    }

    @Test
    void 보상_트랜잭션이_실패하면_주문은_실패하고_재고는_복구된채_남는다() {
        startApplications();

        CancelOrderResponse response = invokeCancel(CancelFailurePoint.INVENTORY_COMPENSATE);

        assertThat(response.status()).isEqualTo("CANCEL_REQUESTED");

        awaitUntil(() ->
                order().getStatus() == OrderStatus.CANCEL_FAILED
                        && inventoryRestoreOperations().count() == 1
                        && paymentRefundOperations().count() == 0
        );

        assertThat(order().getStatus()).isEqualTo(OrderStatus.CANCEL_FAILED);
        assertThat(inventoryStock()).isEqualTo(10);
        assertThat(payment().getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(payment().getRefundedAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(inventoryRestoreOperations().count()).isEqualTo(1);
        assertThat(paymentRefundOperations().count()).isEqualTo(0);
        assertThat(inventoryCompensationOperations().count()).isEqualTo(0);
    }

    private void startApplications() {
        int orderPort = freePort();
        int inventoryPort = freePort();
        int paymentPort = freePort();
        String suffix = UUID.randomUUID().toString();

        inventoryContext = new SpringApplicationBuilder(InventoryServiceApplication.class)
                .run(
                        "--spring.application.name=inventory-service-test",
                        "--server.port=" + inventoryPort,
                        "--spring.datasource.url=jdbc:h2:mem:inventorydb-" + suffix + ";MODE=MYSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
                        "--services.order.base-url=http://127.0.0.1:" + orderPort,
                        "--services.payment.base-url=http://127.0.0.1:" + paymentPort
                );

        paymentContext = new SpringApplicationBuilder(PaymentServiceApplication.class)
                .run(
                        "--spring.application.name=payment-service-test",
                        "--server.port=" + paymentPort,
                        "--spring.datasource.url=jdbc:h2:mem:paymentdb-" + suffix + ";MODE=MYSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
                        "--services.order.base-url=http://127.0.0.1:" + orderPort,
                        "--services.inventory.base-url=http://127.0.0.1:" + inventoryPort
                );

        orderContext = new SpringApplicationBuilder(OrderServiceApplication.class)
                .run(
                        "--spring.application.name=order-service-test",
                        "--server.port=" + orderPort,
                        "--spring.datasource.url=jdbc:h2:mem:orderdb-" + suffix + ";MODE=MYSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
                        "--services.inventory.base-url=http://127.0.0.1:" + inventoryPort,
                        "--services.payment.base-url=http://127.0.0.1:" + paymentPort
                );

        this.orderBaseUrl = "http://127.0.0.1:" + orderPort;
    }

    private String orderBaseUrl;

    private CancelOrderResponse invokeCancel(CancelFailurePoint failurePoint) {
        RestClient restClient = RestClient.create(orderBaseUrl);
        String uri = failurePoint == CancelFailurePoint.NONE
                ? "/orders/1/cancel"
                : "/orders/1/cancel?failAt=" + failurePoint.name();

        return restClient.post()
                .uri(uri)
                .retrieve()
                .body(CancelOrderResponse.class);
    }

    private PurchaseOrder order() {
        return orderContext.getBean(PurchaseOrderRepository.class)
                .findById(1L)
                .orElseThrow();
    }

    private Payment payment() {
        return paymentContext.getBean(PaymentRepository.class)
                .findById("PAY-100")
                .orElseThrow();
    }

    private int inventoryStock() {
        return inventoryContext.getBean(InventoryItemRepository.class)
                .findById("SKU-001")
                .orElseThrow()
                .getStock();
    }

    private InventoryRestoreOperationRepository inventoryRestoreOperations() {
        return inventoryContext.getBean(InventoryRestoreOperationRepository.class);
    }

    private InventoryCompensationOperationRepository inventoryCompensationOperations() {
        return inventoryContext.getBean(InventoryCompensationOperationRepository.class);
    }

    private PaymentRefundOperationRepository paymentRefundOperations() {
        return paymentContext.getBean(PaymentRefundOperationRepository.class);
    }

    private void awaitUntil(Supplier<Boolean> condition) {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(5));
        while (Instant.now().isBefore(deadline)) {
            if (condition.get()) {
                return;
            }
            sleep(100);
        }
        assertThat(condition.get()).isTrue();
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for saga completion", exception);
        }
    }

    private static int freePort() {
        try (ServerSocket serverSocket = new ServerSocket(0)) {
            return serverSocket.getLocalPort();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to allocate random port", exception);
        }
    }

    private static void close(ConfigurableApplicationContext context) {
        if (context != null) {
            context.close();
        }
    }
}
