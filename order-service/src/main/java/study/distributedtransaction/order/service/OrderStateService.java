package study.distributedtransaction.order.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import study.distributedtransaction.order.domain.PurchaseOrder;
import study.distributedtransaction.order.domain.PurchaseOrderRepository;

@Service
public class OrderStateService {

    private final PurchaseOrderRepository orderRepository;

    public OrderStateService(PurchaseOrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Transactional
    public PurchaseOrder markCancelRequested(Long orderId) {
        PurchaseOrder order = findOrder(orderId);
        order.requestCancel();
        return order;
    }

    @Transactional
    public void completeCancel(Long orderId) {
        findOrder(orderId).completeCancel();
    }

    @Transactional
    public void failCancel(Long orderId) {
        findOrder(orderId).failCancel();
    }

    private PurchaseOrder findOrder(Long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found. orderId=" + orderId));
    }
}
