# distributed-transaction

`study/saga-orchestration` 브랜치는 주문 취소를 Saga Orchestration 방식으로 구현한 예제다.

주문 취소는 하나의 작업처럼 보이지만 실제로는 여러 서비스가 나누어 처리한다.

- `order-service`는 주문 상태를 변경한다.
- `inventory-service`는 재고를 복구하거나 다시 차감한다.
- `payment-service`는 환불을 처리한다.

이 브랜치에서는 `order-service`가 **orchestrator** 역할을 맡아 전체 흐름을 직접 제어한다.

## Services

| Service | Port | DB | Responsibility |
| --- | ---: | --- | --- |
| order-service | 8081 | `orderdb` | 주문 상태 변경, Saga 상태 관리, orchestration |
| inventory-service | 8082 | `inventorydb` | 재고 복구, 재고 보상 차감 |
| payment-service | 8083 | `paymentdb` | 결제 환불 |

각 서비스는 독립적인 Spring Boot 애플리케이션이며, H2 인메모리 DB를 사용한다.

## Flow

이 브랜치의 주문 취소 흐름은 아래 순서로 진행된다.

1. `order-service`가 주문 상태를 `CANCEL_REQUESTED`로 변경한다.
2. `order-service`가 Saga 로그를 생성한다.
3. `order-service`가 `inventory-service`에 재고 복구를 요청한다.
4. `order-service`가 `payment-service`에 환불을 요청한다.
5. 환불이 성공하면 주문 상태를 `CANCELLED`로 변경한다.
6. 환불이 실패하면 `inventory-service`에 재고 차감 보상을 요청한다.
7. 보상 결과에 따라 Saga 상태와 주문 상태를 마무리한다.

participant가 스스로 다음 단계를 이어가는 것이 아니라, orchestrator가 다음 호출과 보상 호출을 직접 결정한다는 점이 핵심이다.

## Code Structure

### order-service

- `OrderController`
  - 주문 취소 API와 Saga 조회 API를 제공한다.
- `OrderCancelService`
  - orchestrator 역할을 한다.
  - 재고 복구, 환불, 보상 차감 호출 순서를 제어한다.
  - Saga 상태를 기록한다.
- `OrderStateService`
  - order DB에 대한 로컬 트랜잭션을 담당한다.
- `CancelOrderSaga`
  - orchestration 진행 상태를 저장한다.
- `CancelOrderSagaStateService`
  - Saga 상태 갱신용 로컬 트랜잭션을 담당한다.

### inventory-service

- `InventoryController`
  - orchestrator가 호출하는 복구/차감 API를 제공한다.
- `InventoryRestoreService`
  - 재고 복구 로컬 트랜잭션을 처리한다.
- `InventoryDeductService`
  - 보상 차감 로컬 트랜잭션을 처리한다.

### payment-service

- `PaymentController`
  - orchestrator가 호출하는 환불 API를 제공한다.
- `PaymentRefundService`
  - 환불 로컬 트랜잭션을 처리한다.

## Transaction Model

이 브랜치에는 분산 트랜잭션 전체를 하나로 묶는 `@Transactional`은 없다.

대신 각 서비스가 자기 DB에 대해서만 짧은 로컬 트랜잭션을 수행한다.

- `order-service`: 주문 상태 변경, Saga 상태 변경
- `inventory-service`: 재고 복구, 재고 차감
- `payment-service`: 환불 처리

실패 시에는 과거 트랜잭션을 롤백하지 않고, 이미 성공한 작업을 되돌리기 위해 새로운 로컬 트랜잭션으로 반대 동작을 수행한다.

예를 들어:

- 재고 복구 `+1`
- 환불 실패
- 재고 보상 차감 `-1`

이 흐름이 Saga의 보상 트랜잭션이다.

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

재고 복구 실패:

```powershell
Invoke-RestMethod -Method Post "http://localhost:8081/orders/1/cancel?failAt=INVENTORY"
```

재고 복구 후 orchestrator 내부 실패:

```powershell
Invoke-RestMethod -Method Post "http://localhost:8081/orders/1/cancel?failAt=AFTER_INVENTORY"
```

환불 실패 후 보상:

```powershell
Invoke-RestMethod -Method Post "http://localhost:8081/orders/1/cancel?failAt=PAYMENT"
```

보상 트랜잭션 실패:

```powershell
Invoke-RestMethod -Method Post "http://localhost:8081/orders/1/cancel?failAt=INVENTORY_COMPENSATION"
```

상태 확인:

```powershell
Invoke-RestMethod http://localhost:8081/orders
Invoke-RestMethod http://localhost:8082/inventory
Invoke-RestMethod http://localhost:8083/payments
Invoke-RestMethod http://localhost:8081/saga/cancel-orders
Invoke-RestMethod http://localhost:8082/inventory/restore-operations
Invoke-RestMethod http://localhost:8082/inventory/deduct-operations
Invoke-RestMethod http://localhost:8083/payments/refund-operations
```

## Test

통합 테스트는 `order-service` 기준으로 작성했다.

- `SagaOrchestrationIntegrationTest`
  - 정상 취소
  - 재고 복구 실패
  - 재고 복구 후 orchestrator 내부 실패
  - 환불 실패 후 보상 성공
  - 환불 실패 후 보상 실패

실행:

```powershell
.\gradlew.bat test --tests study.distributedtransaction.order.SagaOrchestrationIntegrationTest
```

테스트는 서비스 3개를 각각 띄운 뒤 HTTP 호출로 실제 orchestration 흐름을 검증한다.

## H2 Console

- order-service: http://localhost:8081/h2-console
- inventory-service: http://localhost:8082/h2-console
- payment-service: http://localhost:8083/h2-console

각 서비스의 JDBC URL은 해당 `application.yml`의 `spring.datasource.url` 값을 사용한다.
