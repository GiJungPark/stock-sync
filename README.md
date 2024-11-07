# 요구사항
- 재고를 감소시킨다.
- 총 재고는 100개이며, 100번의 요청 후 재고는 0개가 되어야 한다.
- 수평 확장된 서버 환경에서도 재고 감소가 정확하게 처리되어야 한다.

<br>

# 구현
*해당하는 Pull Request 또는 Commit 링크가 삽입되어 있습니다.*
## [재고 감소 기능](https://github.com/GiJungPark/stock-sync/pull/1)
### 상세 내용
- MySQL과 JPA를 사용하여, 재고를 감소시킨 후 저장한다.
- 재고는 엔티티 필드에 포함하여, 요청한 개수만큼 감소한다.
  - 만약 요청한 개수가 남은 재고보다 크다면 예외를 발생시킨다.

### 문제점
- 여러 쓰레드가 동시에 요청하는 테스트 케이스에서, 이 구현 방식은 요청 100번에 대해 재고 100개가 정확히 소진되지 않을 가능성이 있다.

<br>

## [Synchronized](https://github.com/GiJungPark/stock-sync/pull/2)
### 상세 내용
- @Transactional 제거후, @Synchronized 애너테이션을 사용하여 메서드에 한 번에 하나의 쓰레드만 접근하도록 설정한다.
- 메서드가 완료될 때까지 다른 스레드의 접근이 차단되어 Race Condition이 발생하지 않는다.

### @Transactional & @Synchronized
- Spring의 @Transactional은 메서드 호출 시 트랜잭션을 시작하고, 메서드가 종료되면 트랜잭션을 종료하여 데이터베이스에 반영한다.
- 트랜잭션이 종료된 시점에서 실제 데이터베이스에 업데이트가 반영되기 전, 다른 쓰레드가 재고 감소 메서드를 호출하면 갱신되지 않은 재고 값을 참조하게 되어 동시성 문제가 발생할 수 있다.
- 따라서 @Transactional과 @Synchronized를 함께 사용할 경우, 요청 100번에 대해 재고 100개가 정확히 소진되지 않을 가능성이 있다.

### 문제점
- 단일 서버 환경에서는 Race Condition을 방지할 수 있는 방법이다.
- 수평 확장된 서버 환경에서는 각 서버가 독립적으로 메서드의 동시성을 관리하므로, 여전히 Race Condition이 발생할 수 있다.

<br>

## [MySQL Lock](https://github.com/GiJungPark/stock-sync/pull/3)
### [Pessimistic Lock](https://github.com/GiJungPark/stock-sync/pull/3/commits/bccd40d83356a0620c2414d3041dbb7874f703b7)
#### 정의
- 비관적 락이라고 불리며, 데이터에 실제로 Lock을 걸어 데이터 정합성을 보장하는 방법이다.
- exclusive lock(쓰기 잠금)을 걸게되면, 다른 트랜젝션에서는 Lock이 해제될 때까지 데이터에 접근할 수 없게 된다.
  - 이로인해 Dead Lock이 발생할 위험이 있다. 
    - 하지만 Timeout 속성을 설정하여 Dead Lock 위험을 줄일 수 있다.

#### 상세 내용
- @Lock 애너테이션을 사용하여 Pessimistic Lock을 설정한다.
  - @Lock(LockModeType.PESSIMISTIC_WRITE)
  - @Lock: Spring Data Jpa에서 제공하는 기능으로, 특정 데이터베이스 쿼리에 대한 잠금 모드를 지정하는데 사용된다.

#### 장점
- 충돌이 빈번하게 발생하는 경우, Optimistic Lock 보다 성능이 좋을 수 있다.
- Lock을 통해 데이터를 업데이트하기 때문에 데이터 정합성이 보장된다.

#### 단점
- 별도의 Lock이 걸리기 때문에 성능 저하가 발생할 수 있다.

<br>

### [Optimistic Lock](https://github.com/GiJungPark/stock-sync/pull/3/commits/9d9b03fd73fe4ac86c6daae422515a5310cc8256)
#### 정의
- 낙관적 락이라고 불리며, 실제 Lock을 걸지 않고 버전을 이용해 정합성을 유지하는 방법이다.
- 데이터를 읽고 업데이트할 때, 현재 읽은 버전과 데이터베이스의 버전을 비교하여 동일할 경우에만 업데이트를 진행한다.
  - 읽은 후 데이터에 변경 사항이 생긴 경우, 애플리케이션에서 데이터를 다시 읽고 작업을 수행해야 한다.

#### 상세 내용
- @Version과 @Lock을 애너테이션을 사용해 Optimistic Lock을 설정한다.
  - @Lock(LockModeType.OPTIMISTIC)
- 읽은 후 데이터에 변경 사항이 생긴 경우, 재고 감소 메서드를 다시 호출할 수 있도록 한다.

#### 장점
- 별도의 Lock을 사용하지 않아 성능상의 이점이 있다.

#### 단점
- 업데이트를 실패한 경우의 재시도 로직을 개발자가 직접 작성해야 한다.
- 충돌이 빈번하게 발생하면 성능이 떨어질 수 있다.
  - 이러한 상황에서는 Pessimistic Lock을 사용하는 것이 더 적합하다.

<br>

### [Named Lock](https://github.com/GiJungPark/stock-sync/pull/3/commits/64b72aa1b088abd9a3fc81d89de46a86aa37c3d3)
#### 정의
- 특정 이름을 가진 Lock을 흭득하여 해제하기 전까지, 다른 세션에서 해당 Lock을 흭득하지 못하도록 하는 방법이다.
- Transaction이 종료되더라도 Lock은 자동으로 해제되지 않는다.
  - 별도의 명령어로 해제를 수행하거나 선점 시간이 만료되어야 Lock이 해제된다.

#### 상세 내용 
- 별도의 Repository를 만들고 JPA의 Native Query를 사용하여, Lock을 획득 및 해제하는 쿼리를 작성한다.
  - Lock 흭득 쿼리: GET_LOCK()
  - Lock 해제 쿼리: RELEASE_LOCK()
- Lock을 흭득하고 재고 감소 메서드가 수행되면 Lock을 반납하도록 한다.

#### 주의 사항
- 실제 사용시, 데이터 소스를 분리하는 것이 좋다.
  - 커넥션 풀이 부족해져, 다른 서비스에도 영향을 미칠 수 있기 때문이다.

#### 장점
- 충돌이 빈번하게 발생하는 경우, 안정적인 정합성을 유지할 수 있다.
- update가 아닌 insert 작업인 경우, 기준을 잡을 레코드가 존재하기 않기 때문에 Named Lock을 활용할 수 있다.

#### 단점
- 별도의 Lock 획득과 해제 로직이 필요하다.
- Transaction과 별개로 Lcok이 관리되므로, Lock 해제 누락에 주의해야 한다.

<br>

## [Redis]()

<br>

## MySQL vs Redis