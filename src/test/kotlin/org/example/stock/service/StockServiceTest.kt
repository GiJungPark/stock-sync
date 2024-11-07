package org.example.stock.service

import org.assertj.core.api.Assertions.assertThat
import org.example.stock.domain.Stock
import org.example.stock.repository.StockJpaRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import kotlin.jvm.optionals.getOrNull

@SpringBootTest
class StockServiceTest {

    @Autowired
    private lateinit var stockService: PessimisticLockStockService

    @Autowired
    private lateinit var stockJpaRepository: StockJpaRepository

    @BeforeEach
    fun setUp() {
        stockJpaRepository.saveAndFlush(Stock(productId = 1L, quantity = 100L))
    }

    @AfterEach
    fun tearDown() {
        stockJpaRepository.deleteAllInBatch()
    }

    @DisplayName("재고를 감소한다.")
    @Test
    fun decrease() {
        // given
        val stockId = 1L
        val quantity = 1L

        // when
        stockService.decrease(stockId, quantity)

        // then
        val savedStock = stockJpaRepository.findById(stockId).orElseThrow()
        assertThat(savedStock.quantity).isEqualTo(99L)
    }

    @DisplayName("100번의 재고 감소 요청이 있는 경우, 재고는 0이 된다.")
    @Test
    fun decreaseMultipleConcurrentCall() {
        // given
        val threadCount = 100
        val executorService = Executors.newFixedThreadPool(32)
        val latch = CountDownLatch(threadCount)

        // when
        for (i in 0 until threadCount) {
            executorService.submit {
                try {
                    stockService.decrease(1L, 1L)
                } finally {
                    latch.countDown()
                }
            }
        }

        latch.await()

        // then
        val stock = stockJpaRepository.findById(1L).orElseThrow()
        assertThat(stock.quantity).isEqualTo(0L)
    }
}