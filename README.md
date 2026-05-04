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

`study/saga-orchestration` 브랜치는 Saga의 orchestration 방식을 실험합니다.
order-service가 Saga orchestrator 역할을 맡아 각 서비스의 로컬 트랜잭션과 보상 트랜잭션을 명시적으로 지시합니다.

1. order-service: 주문을 `CANCEL_REQUESTED`로 변경하고 Saga 로그 생성
2. order-service -> inventory-service: 재고 복구 명령
3. order-service -> payment-service: 환불 명령
4. 환불 성공 시 order-service가 주문을 `CANCELLED`로 변경
5. 환불 실패 시 order-service가 inventory-service에 재고 차감 보상 명령

각 서비스는 자기 로컬 트랜잭션만 짧게 수행하고, 전체 흐름과 보상 판단은 orchestrator가 관리합니다.

## Run

터미널 3개에서 각각 실행합니다.

```powershell
.\gradlew.bat :inventory-service:bootRun
.\gradlew.bat :payment-service:bootRun
.\gradlew.bat :order-service:bootRun
```

## Try

정상 Saga 취소:

```powershell
Invoke-RestMethod -Method Post http://localhost:8081/orders/1/cancel
```

재고 복구 후 결제 실패 및 재고 보상:

```powershell
Invoke-RestMethod -Method Post "http://localhost:8081/orders/1/cancel?failAt=PAYMENT"
```

재고 보상 실패:

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

## Branch Study Guide

추천 브랜치:

```powershell
git checkout study/2pc
git checkout study/3pc
git checkout study/saga-orchestration
```

각 브랜치에서 아래처럼 설계를 바꿔보면 차이가 잘 보입니다.

### 2PC

- order-service에 coordinator 역할 추가
- inventory/payment에 `prepare`, `commit`, `rollback` endpoint 추가
- participant DB에 pending operation 저장
- coordinator 장애 시 pending 상태가 얼마나 오래 잠기는지 관찰

### 3PC

- 2PC의 `prepare` 뒤에 `preCommit` 단계 추가
- timeout 처리와 participant 자율 결정 로직 추가
- 네트워크 분리 상황에서 정말 blocking이 사라지는지 한계를 관찰

### Saga

- `CancelOrderSaga` 상태 테이블 추가
- orchestration 방식으로 order-service가 전체 Saga 흐름 제어
- 재고 복구 성공 후 환불 실패 시 `deduct inventory` 보상 트랜잭션 수행
- choreography 브랜치와 비교하면 중앙 제어는 명확하지만 orchestrator 장애 영향이 커짐

## H2 Console

- order-service: http://localhost:8081/h2-console
- inventory-service: http://localhost:8082/h2-console
- payment-service: http://localhost:8083/h2-console

각 서비스의 JDBC URL은 해당 `application.yml`의 `spring.datasource.url`을 사용합니다.
