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
import kotlin.jvm.optionals.getOrNull

@SpringBootTest
class StockServiceTest {

    @Autowired
    private lateinit var stockService: StockService

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

}