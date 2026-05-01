# distributed-transaction

분산 트랜잭션 실험실입니다. 주문 취소 및 환불 흐름을 작은 MSA처럼 나누어 직접 설계하고 비교해볼 수 있습니다.

## Services

| Service | Port | DB | Responsibility |
| --- | ---: | --- | --- |
| order-service | 8081 | `orderdb` | 주문 상태 변경 |
| inventory-service | 8082 | `inventorydb` | 재고 복구 |
| payment-service | 8083 | `paymentdb` | 결제 환불 |

각 서비스는 독립 Spring Boot 애플리케이션이고, H2도 서비스별 인메모리 DB를 사용합니다.
즉 한 JVM/한 DB 트랜잭션으로 묶을 수 없는 상황을 일부러 만들었습니다.

## Current Flow

기본 스켈레톤은 의도적으로 단순한 순차 호출 방식입니다.

1. order-service: 주문을 `CANCEL_REQUESTED`로 변경
2. order-service -> inventory-service: 재고 복구
3. order-service -> payment-service: 환불
4. order-service: 주문을 `CANCELLED`로 변경

중간 실패를 넣으면 각 서비스의 DB 상태가 어긋납니다. 이 상태를 관찰한 뒤 직접 설계를 바꿔보는 것이 목표입니다.
