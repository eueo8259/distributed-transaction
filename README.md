# distributed-transaction

이 브랜치는 `study/saga-choreography` 기준의 학습용 예제다.

주문 취소를 예제로 삼아, 분산 트랜잭션을 **Saga Choreography** 방식으로 어떻게 구현할 수 있는지 코드로 보여준다.  
핵심은 한 서비스가 다음 서비스를 직접 호출하지 않고, **이벤트를 발행하고 다음 서비스가 그 이벤트에 반응하는 구조**에 있다.

## Services

| Service | Port | DB | 역할 |
| --- | ---: | --- | --- |
| order-service | 8081 | `orderdb` | 주문 상태 변경, Saga 시작, 최종 상태 반영 |
| inventory-service | 8082 | `inventorydb` | 재고 복구, 재고 보상 트랜잭션 |
| payment-service | 8083 | `paymentdb` | 환불 처리 |

각 서비스는 독립적인 Spring Boot 애플리케이션이며, H2 in-memory DB를 사용한다.

## Flow

주문 취소 Saga는 아래 순서로 흐른다.

1. `order-service`가 주문 상태를 `CANCEL_REQUESTED`로 변경한다.
2. `order-service`가 `OrderCancelRequestedEvent`를 발행한다.
3. `inventory-service`가 이벤트를 받아 재고를 복구한다.
4. `inventory-service`가 `InventoryRestoredEvent`를 발행한다.
5. `payment-service`가 이벤트를 받아 환불을 수행한다.
6. 환불이 성공하면 `PaymentRefundedEvent`가 발행되고, `order-service`가 주문 상태를 `CANCELLED`로 반영한다.
7. 환불이 실패하면 `PaymentRefundFailedEvent`가 발행되고, `inventory-service`가 보상 트랜잭션으로 재고를 다시 차감한다.
8. 보상이 끝나면 `InventoryCompensatedEvent`가 발행되고, `order-service`가 주문 상태를 `CANCEL_FAILED`로 반영한다.

## Code Structure

각 서비스는 같은 패턴으로 나뉜다.

### `SagaEventController`

이벤트를 받는 진입점이다.  
다른 서비스가 발행한 이벤트를 HTTP 요청으로 받아 `SagaService`로 넘긴다.

예시:

- `OrderSagaEventController`
- `InventorySagaEventController`
- `PaymentSagaEventController`

### `SagaService`

Saga 흐름을 제어한다.  
이벤트를 받았을 때 어떤 로컬 트랜잭션을 수행할지 결정하고, 성공 또는 실패에 따라 다음 이벤트를 발행한다.

예시:

- `OrderCancelService`
- `InventorySagaService`
- `PaymentSagaService`

### `LocalTransactionService`

실제 DB 변경을 담당한다.  
`@Transactional`은 이 계층에만 둔다.

예시:

- `InventoryLocalTransactionService`
- `PaymentLocalTransactionService`

이 구조를 통해 **Saga 흐름 제어**와 **로컬 DB 트랜잭션 경계**를 분리해서 볼 수 있다.

## Transaction Model

이 예제에는 분산 트랜잭션 전체를 감싸는 하나의 전역 트랜잭션이 없다.  
대신 각 서비스가 자기 DB에 대해서만 짧은 **로컬 트랜잭션**을 수행하고, 그 결과를 이벤트로 연결한다.

정리하면 다음과 같다.

- `order-service` 로컬 트랜잭션
- `inventory-service` 로컬 트랜잭션
- `payment-service` 로컬 트랜잭션
- 필요 시 `inventory-service` 보상 트랜잭션

즉 rollback으로 되돌리는 구조가 아니라, 이미 커밋된 작업은 **보상 트랜잭션**으로 반대 동작을 수행한다.

## Tests

이 브랜치에는 Choreography 흐름을 실제로 검증하는 통합 테스트가 포함되어 있다.

- 파일: `order-service/src/test/java/study/distributedtransaction/order/SagaChoreographyIntegrationTest.java`

이 테스트는 세 서비스를 함께 띄운 뒤, 주문 취소 API를 실제로 호출하고 각 서비스의 repository 상태를 확인한다.

검증하는 시나리오는 다음과 같다.

1. 정상 취소
2. 재고 복구 실패
3. 환불 실패 후 보상 성공
4. 환불 실패 후 보상 실패

실행 명령:

```powershell
.\gradlew.bat test --tests study.distributedtransaction.order.SagaChoreographyIntegrationTest
```

## Run

각 서비스는 아래처럼 따로 실행할 수 있다.

```powershell
.\gradlew.bat :inventory-service:bootRun
.\gradlew.bat :payment-service:bootRun
.\gradlew.bat :order-service:bootRun
```

## Try

정상 취소:

```powershell
Invoke-RestMethod -Method Post http://localhost:8081/orders/1/cancel
```

환불 실패 후 보상:

```powershell
Invoke-RestMethod -Method Post "http://localhost:8081/orders/1/cancel?failAt=PAYMENT_REFUND"
```

재고 복구 실패:

```powershell
Invoke-RestMethod -Method Post "http://localhost:8081/orders/1/cancel?failAt=INVENTORY_RESTORE"
```

환불 실패 후 보상 트랜잭션 실패:

```powershell
Invoke-RestMethod -Method Post "http://localhost:8081/orders/1/cancel?failAt=INVENTORY_COMPENSATE"
```

## H2 Console

- order-service: http://localhost:8081/h2-console
- inventory-service: http://localhost:8082/h2-console
- payment-service: http://localhost:8083/h2-console
