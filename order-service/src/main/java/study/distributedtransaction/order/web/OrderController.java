package study.distributedtransaction.order.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import study.distributedtransaction.common.CancelOrderResponse;
import study.distributedtransaction.order.domain.CancelOrderSaga;
import study.distributedtransaction.order.domain.PurchaseOrder;
import study.distributedtransaction.order.domain.PurchaseOrderRepository;
import study.distributedtransaction.order.service.CancelFailurePoint;
import study.distributedtransaction.order.service.OrderCancelService;

import java.util.List;

@RestController
public class OrderController {

    private final PurchaseOrderRepository orderRepository;
    private final OrderCancelService orderCancelService;

    public OrderController(PurchaseOrderRepository orderRepository, OrderCancelService orderCancelService) {
        this.orderRepository = orderRepository;
        this.orderCancelService = orderCancelService;
    }

    @GetMapping("/orders")
    public List<PurchaseOrder> findAll() {
        return orderRepository.findAll();
    }

    @GetMapping("/orders/{orderId}")
    public PurchaseOrder findOne(@PathVariable Long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found. orderId=" + orderId));
    }

    @PostMapping("/orders/{orderId}/cancel")
    public CancelOrderResponse cancel(
            @PathVariable Long orderId,
            @RequestParam(defaultValue = "NONE") CancelFailurePoint failAt
    ) {
        return orderCancelService.cancel(orderId, failAt);
    }

    @GetMapping("/saga/cancel-orders")
    public List<CancelOrderSaga> findSagas() {
        return orderCancelService.findSagas();
    }
}
