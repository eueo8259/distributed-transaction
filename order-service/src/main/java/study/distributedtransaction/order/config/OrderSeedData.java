package study.distributedtransaction.order.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import study.distributedtransaction.order.domain.PurchaseOrder;
import study.distributedtransaction.order.domain.PurchaseOrderRepository;

import java.math.BigDecimal;

@Component
public class OrderSeedData implements ApplicationRunner {

    private final PurchaseOrderRepository orderRepository;

    public OrderSeedData(PurchaseOrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (orderRepository.count() == 0) {
            orderRepository.save(new PurchaseOrder("SKU-001", 2, "PAY-100", new BigDecimal("30000")));
        }
    }
}
