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

### Pessimistic & Optimistic 부하 테스트
#### 충돌이 많은 경우
<img width="700" alt="Pessimistic" src="https://github.com/user-attachments/assets/c6e27e93-d210-405f-84d3-0c5020d9fb30">

<br>

Pessimistic Lock CPU 점유율: 최대 15.23%

<br>

<img width="700" alt="Optimistic" src="https://github.com/user-attachments/assets/5b3513dc-f892-47c8-8d43-200e69b0f66a">

<br>

Optimistic Lock CPU 점유율: 최대 53.43%

<br>

- 충돌이 빈번하게 발생하는 경우, Pessimistic Lock을 사용했을 때 CPU 점유율이 상대적으로 낮은 것을 확인할 수 있다.

<br>

#### 충돌이 적은 경우
<img width="700" alt="Pessimistic 충돌 적음" src="https://github.com/user-attachments/assets/19528bc6-4407-4cd9-a148-146a9d649b18">

<br>

Pessimistic Lock CPU 점유율: 최대 22.11% 

<br>

<img width="700" alt="Optimistic 충돌 적음" src="https://github.com/user-attachments/assets/84b493b7-d892-46aa-bba0-d5bb0e176e54">

<br>

Optimistic Lock CPU 점유율: 최대 13.68%

<br>

- 충돌 발생이 적은 경우, Optimistic Lock을 사용했을 때 CPU 점유율이 상대적으로 낮은 것을 확인할 수 있다.

<br>

## [Redis](https://github.com/GiJungPark/stock-sync/pull/4)
### [Lettuce](https://github.com/GiJungPark/stock-sync/pull/4/commits/4f1f645f552053e2f737ec9d29a508fd30366cdc)
#### 정의
- 고성능, 확장 가능, 쓰레드 안전한 Redis 자바 클라이언트이다.
- 동기와 비동기 통신을 모두 지원한다.

#### 상세 내용
- SETNX 명령어를 활용하여, 분산락을 구현한다.
  - SENTX 명령어: "SET if Not Exists"의 약자로, 주어진 키가 존재하지 않을 때만 값을 설정한다.
- Lock을 흭득할 때 까지 대기하며, Lock을 흭득하면 재고 감소 메서드를 수행하고 Lock을 반납한다.

#### 장점
- 구현이 간단하다.
  - Spring Data Redis를 이용하면 Lettuce가 기본이기 때문에, 별도의 라이브러리를 사용하지 않아도 된다.

#### 단점
- Spin Lock 방식이므로, 동시에 많은 쓰레드가 Lock 흭득 대기 상태라면 Redis에 부하가 갈 수 있다.
  - Spin Lock: Race Condition 상황에서 Critical section에 진입 불가능할 때, 진입이 가능할 때까지 루프를 돌면서 재시도하는 방식

<br>

### [Redisson](https://github.com/GiJungPark/stock-sync/pull/4/commits/86e0a7a19cac6b674a537972a234c6ec6f0826a1)
#### 정의
- 분산락 구현을 위한 다양한 기능을 제공하는 Redis 자바 클라이언트이다.
- Lock 흭득 재시도를 기본적으로 제공한다.

#### 상세 내용
- RedissonClient을 사용하여 Lock을 흭득하고 재고 감소 메서드를 수행하고 Lock을 반납한다.

#### 장점
- pub-sub 방식으로 구현되어 있기 때문에, Lettuce에 비해 Redis 부하가 덜 간다.
  - pub-sub 방식: 메시지를 발행(Publish)하고 구독(Subscribe)하는 방식

#### 단점
- 별도의 라이브러리를 사용해야 하며, Lock을 라이브러리에서 지원해주기 때문에 사용법을 학습해야 한다.

<br>

### Lettuce & Redisson 부하 비교 테스트
#### 재고 감소 로직이 빠를 때
<img width="700" alt="Lettuce" src="https://github.com/user-attachments/assets/9cbb5fac-e9a4-46c5-ba54-0cae7085b762">

<br>

Lettuce의 CPU 점유율: 최대 2.74%

<br>

<img width="700" alt="Redisson" src="https://github.com/user-attachments/assets/5b1199c6-8f65-462a-a028-cca7f4838d11">

<br>

Redisson의 CPU 점유율: 최대 3.52%

<br>

- 재고 감소 로직의 처리 시간이 짧고 대기 상태가 적다. 
  - Lettuce: 대기 시간이 짧고 자원을 빨리 얻을 수 있기 때문에, 충돌이 적게 발생하게 되어 CPU 점유율이 상대적으로 낮은 것을 확인할 수 있다.

<br>

#### 재고 감소의 로직이 느릴 때 (수행 시간을 1초 가량 증가 시킴)
<img width="700" alt="Lettuce 메서드 실행시간이 오래 걸리는 경우" src="https://github.com/user-attachments/assets/8a34416a-af9f-4f58-a6db-92b3e26b5d42">

<br>

Lettuce의 CPU 점유율: 최대 10.83%

<br>

<img width="700" alt="Redisson 메서드 실행 시간이 오래 걸리는 경우" src="https://github.com/user-attachments/assets/31a84417-ed77-4277-86b6-92cec76fa27a">

<br>

Redisson의 CPU 점유율: 최대 2.29%

<br>

- 재고 감소 로직의 실행 시간이 늘어나면서 대기 상태에 놓이는 일이 빈번해진다.
- 해당 경우에는 대기 상태에 놓이는 일이 빈번하게 발생하기 때문에 Lettuce의 평균 CPU 점유율과, 최대 CPU 점유율이 더 높은 것을 확인 할 수 있다.
  - Lettuce: 기다려야 하는 시간이 길어지면서 CPU 점유율이 급격히 증가한 것을 확인할 수 있다.
  - Redisson: Pub-Sub 방식으로 동기화가 이루어지기 때문에, CPU 점유율이 낮은 것을 확인할 수 있다.