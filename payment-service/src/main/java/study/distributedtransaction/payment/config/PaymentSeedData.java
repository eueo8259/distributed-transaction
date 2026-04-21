package study.distributedtransaction.payment.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import study.distributedtransaction.payment.domain.Payment;
import study.distributedtransaction.payment.domain.PaymentRepository;

import java.math.BigDecimal;

@Component
public class PaymentSeedData implements ApplicationRunner {

    private final PaymentRepository paymentRepository;

    public PaymentSeedData(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (paymentRepository.count() == 0) {
            paymentRepository.save(new Payment("PAY-100", 1L, new BigDecimal("30000")));
        }
    }
}
