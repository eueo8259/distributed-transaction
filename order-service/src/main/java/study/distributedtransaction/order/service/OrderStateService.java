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
        // order-service 내부에서만 열리는 로컬 트랜잭션이다.
        // 여기에는 다른 서비스 DB가 함께 묶이지 않는다.
        PurchaseOrder order = findOrder(orderId);
        order.requestCancel();
        return order;
    }

    @Transactional
    public void completeCancel(Long orderId) {
        // PaymentRefundedEvent가 order-service에 도착했을 때 호출된다.
        findOrder(orderId).completeCancel();
    }

    @Transactional
    public void failCancel(Long orderId) {
        // 재고 복구 실패 이벤트가 오거나, 환불 실패 후 보상까지 끝났을 때 호출된다.
        findOrder(orderId).failCancel();
    }

    private PurchaseOrder findOrder(Long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found. orderId=" + orderId));
    }
}
