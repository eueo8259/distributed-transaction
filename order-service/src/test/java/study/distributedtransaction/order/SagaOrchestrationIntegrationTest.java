package study.distributedtransaction.order;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.web.client.RestClient;
import study.distributedtransaction.common.CancelOrderResponse;
import study.distributedtransaction.inventory.InventoryServiceApplication;
import study.distributedtransaction.inventory.domain.InventoryDeductOperationRepository;
import study.distributedtransaction.inventory.domain.InventoryItem;
import study.distributedtransaction.inventory.domain.InventoryItemRepository;
import study.distributedtransaction.inventory.domain.InventoryRestoreOperationRepository;
import study.distributedtransaction.order.OrderServiceApplication;
import study.distributedtransaction.order.domain.CancelOrderSaga;
import study.distributedtransaction.order.domain.CancelOrderSagaRepository;
import study.distributedtransaction.order.domain.CancelOrderSagaStatus;
import study.distributedtransaction.order.domain.OrderStatus;
import study.distributedtransaction.order.domain.PurchaseOrder;
import study.distributedtransaction.order.domain.PurchaseOrderRepository;
import study.distributedtransaction.payment.PaymentServiceApplication;
import study.distributedtransaction.payment.domain.Payment;
import study.distributedtransaction.payment.domain.PaymentRefundOperationRepository;
import study.distributedtransaction.payment.domain.PaymentRepository;
import study.distributedtransaction.payment.domain.PaymentStatus;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.ServerSocket;

import static org.assertj.core.api.Assertions.assertThat;

class SagaOrchestrationIntegrationTest {

    private ConfigurableApplicationContext inventoryContext;
    private ConfigurableApplicationContext paymentContext;
    private ConfigurableApplicationContext orderContext;

    private int inventoryPort;
    private int paymentPort;
    private int orderPort;

    private RestClient orderClient;

    @BeforeEach
    void setUp() {
        inventoryPort = findFreePort();
        paymentPort = findFreePort();
        orderPort = findFreePort();

        inventoryContext = startInventoryService(inventoryPort);
        paymentContext = startPaymentService(paymentPort);
        orderContext = startOrderService(orderPort, inventoryPort, paymentPort);

        orderClient = RestClient.builder()
                .baseUrl("http://127.0.0.1:" + orderPort)
                .build();
    }

    @AfterEach
    void tearDown() {
        if (orderContext != null) {
            orderContext.close();
        }
        if (paymentContext != null) {
            paymentContext.close();
        }
        if (inventoryContext != null) {
            inventoryContext.close();
        }
    }

    @Test
    void cancelOrderCompletesSagaWhenAllStepsSucceed() {
        CancelOrderResponse response = cancelOrder("NONE");

        PurchaseOrder order = orderRepository().findById(1L).orElseThrow();
        InventoryItem item = inventoryRepository().findById("SKU-001").orElseThrow();
        Payment payment = paymentRepository().findById("PAY-100").orElseThrow();
        CancelOrderSaga saga = sagaRepository().findAll().get(0);

        assertThat(response.status()).isEqualTo("CANCELLED");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(item.getStock()).isEqualTo(10);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(payment.getRefundedAmount()).isEqualByComparingTo(new BigDecimal("30000"));
        assertThat(saga.getStatus()).isEqualTo(CancelOrderSagaStatus.COMPLETED);
        assertThat(saga.isInventoryRestored()).isTrue();
        assertThat(saga.isPaymentRefunded()).isTrue();
        assertThat(saga.isInventoryCompensated()).isFalse();
        assertThat(restoreOperationRepository().count()).isEqualTo(1);
        assertThat(refundOperationRepository().count()).isEqualTo(1);
        assertThat(deductOperationRepository().count()).isZero();
    }

    @Test
    void cancelOrderFailsWhenInventoryRestoreFails() {
        CancelOrderResponse response = cancelOrder("INVENTORY");

        PurchaseOrder order = orderRepository().findById(1L).orElseThrow();
        InventoryItem item = inventoryRepository().findById("SKU-001").orElseThrow();
        Payment payment = paymentRepository().findById("PAY-100").orElseThrow();
        CancelOrderSaga saga = sagaRepository().findAll().get(0);

        assertThat(response.status()).isEqualTo("CANCEL_FAILED");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCEL_FAILED);
        assertThat(item.getStock()).isEqualTo(8);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(payment.getRefundedAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(saga.getStatus()).isEqualTo(CancelOrderSagaStatus.FAILED);
        assertThat(saga.isInventoryRestored()).isFalse();
        assertThat(saga.isPaymentRefunded()).isFalse();
        assertThat(restoreOperationRepository().count()).isZero();
        assertThat(refundOperationRepository().count()).isZero();
        assertThat(deductOperationRepository().count()).isZero();
    }

    @Test
    void cancelOrderCompensatesInventoryWhenPaymentRefundFails() {
        CancelOrderResponse response = cancelOrder("PAYMENT");

        PurchaseOrder order = orderRepository().findById(1L).orElseThrow();
        InventoryItem item = inventoryRepository().findById("SKU-001").orElseThrow();
        Payment payment = paymentRepository().findById("PAY-100").orElseThrow();
        CancelOrderSaga saga = sagaRepository().findAll().get(0);

        assertThat(response.status()).isEqualTo("CANCEL_FAILED");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCEL_FAILED);
        assertThat(item.getStock()).isEqualTo(8);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(payment.getRefundedAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(saga.getStatus()).isEqualTo(CancelOrderSagaStatus.INVENTORY_COMPENSATED);
        assertThat(saga.isInventoryRestored()).isTrue();
        assertThat(saga.isPaymentRefunded()).isFalse();
        assertThat(saga.isInventoryCompensated()).isTrue();
        assertThat(restoreOperationRepository().count()).isEqualTo(1);
        assertThat(refundOperationRepository().count()).isZero();
        assertThat(deductOperationRepository().count()).isEqualTo(1);
    }

    @Test
    void cancelOrderLeavesRestoredInventoryWhenCompensationAlsoFails() {
        CancelOrderResponse response = cancelOrder("INVENTORY_COMPENSATION");

        PurchaseOrder order = orderRepository().findById(1L).orElseThrow();
        InventoryItem item = inventoryRepository().findById("SKU-001").orElseThrow();
        Payment payment = paymentRepository().findById("PAY-100").orElseThrow();
        CancelOrderSaga saga = sagaRepository().findAll().get(0);

        assertThat(response.status()).isEqualTo("CANCEL_FAILED");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCEL_FAILED);
        assertThat(item.getStock()).isEqualTo(10);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(payment.getRefundedAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(saga.getStatus()).isEqualTo(CancelOrderSagaStatus.FAILED);
        assertThat(saga.isInventoryRestored()).isTrue();
        assertThat(saga.isPaymentRefunded()).isFalse();
        assertThat(saga.isInventoryCompensated()).isFalse();
        assertThat(restoreOperationRepository().count()).isEqualTo(1);
        assertThat(refundOperationRepository().count()).isZero();
        assertThat(deductOperationRepository().count()).isZero();
        assertThat(saga.getFailureReason()).contains("inventory compensation failed");
    }

    private CancelOrderResponse cancelOrder(String failurePoint) {
        return orderClient.post()
                .uri("/orders/1/cancel?failAt={failAt}", failurePoint)
                .retrieve()
                .body(CancelOrderResponse.class);
    }

    private PurchaseOrderRepository orderRepository() {
        return orderContext.getBean(PurchaseOrderRepository.class);
    }

    private CancelOrderSagaRepository sagaRepository() {
        return orderContext.getBean(CancelOrderSagaRepository.class);
    }

    private InventoryItemRepository inventoryRepository() {
        return inventoryContext.getBean(InventoryItemRepository.class);
    }

    private InventoryRestoreOperationRepository restoreOperationRepository() {
        return inventoryContext.getBean(InventoryRestoreOperationRepository.class);
    }

    private InventoryDeductOperationRepository deductOperationRepository() {
        return inventoryContext.getBean(InventoryDeductOperationRepository.class);
    }

    private PaymentRepository paymentRepository() {
        return paymentContext.getBean(PaymentRepository.class);
    }

    private PaymentRefundOperationRepository refundOperationRepository() {
        return paymentContext.getBean(PaymentRefundOperationRepository.class);
    }

    private ConfigurableApplicationContext startInventoryService(int port) {
        return new SpringApplicationBuilder(InventoryServiceApplication.class)
                .web(WebApplicationType.SERVLET)
                .run(
                        "--server.port=" + port,
                        "--spring.datasource.url=jdbc:h2:mem:inventorydb-" + port + ";MODE=MYSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE"
                );
    }

    private ConfigurableApplicationContext startPaymentService(int port) {
        return new SpringApplicationBuilder(PaymentServiceApplication.class)
                .web(WebApplicationType.SERVLET)
                .run(
                        "--server.port=" + port,
                        "--spring.datasource.url=jdbc:h2:mem:paymentdb-" + port + ";MODE=MYSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE"
                );
    }

    private ConfigurableApplicationContext startOrderService(int port, int inventoryPort, int paymentPort) {
        return new SpringApplicationBuilder(OrderServiceApplication.class)
                .web(WebApplicationType.SERVLET)
                .run(
                        "--server.port=" + port,
                        "--spring.datasource.url=jdbc:h2:mem:orderdb-" + port + ";MODE=MYSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
                        "--services.inventory.base-url=http://127.0.0.1:" + inventoryPort,
                        "--services.payment.base-url=http://127.0.0.1:" + paymentPort
                );
    }

    private int findFreePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to allocate a free port", exception);
        }
    }
}
