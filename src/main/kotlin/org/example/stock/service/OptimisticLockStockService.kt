package org.example.stock.service

import jakarta.transaction.Transactional
import org.example.stock.repository.StockJpaRepository
import org.springframework.stereotype.Service

@Service
class OptimisticLockStockService(
    private val stockJpaRepository: StockJpaRepository,
) {

    @Transactional
    fun decrease(id: Long, quantity: Long) {
        val stock = stockJpaRepository.findByIdWithOptimisticLock(id)

        stock.decrease(quantity)

        stockJpaRepository.saveAndFlush(stock)
    }
}