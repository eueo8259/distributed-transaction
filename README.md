# Distributed Transaction Study

주문 취소 및 환불 흐름을 작은 MSA처럼 나누어 실험하는 학습용 프로젝트입니다.

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

중간 실패를 넣으면 각 서비스의 DB 상태가 어긋납니다. 이 상태를 관찰한 뒤 2PC, 3PC, Saga 브랜치에서 직접 설계를 바꿔보는 것이 목표입니다.

## Run

터미널 3개에서 각각 실행합니다.

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

재고 복구 후 결제 실패:

```powershell
Invoke-RestMethod -Method Post "http://localhost:8081/orders/1/cancel?failAt=PAYMENT"
```

재고 복구 직후 order-service 실패:

```powershell
Invoke-RestMethod -Method Post "http://localhost:8081/orders/1/cancel?failAt=AFTER_INVENTORY"
```

상태 확인:

```powershell
Invoke-RestMethod http://localhost:8081/orders
Invoke-RestMethod http://localhost:8082/inventory
Invoke-RestMethod http://localhost:8083/payments
```

## Branch Study Guide

추천 브랜치:

```powershell
git checkout study/2pc
git checkout study/3pc
git checkout study/saga
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
- 재고 복구 성공 후 환불 실패 시 `restore inventory`의 보상 트랜잭션으로 `deduct inventory` 설계
- orchestration 방식과 choreography 방식 중 하나를 선택해 이벤트/명령 흐름 비교

## H2 Console

- order-service: http://localhost:8081/h2-console
- inventory-service: http://localhost:8082/h2-console
- payment-service: http://localhost:8083/h2-console

각 서비스의 JDBC URL은 해당 `application.yml`의 `spring.datasource.url`을 사용합니다.
