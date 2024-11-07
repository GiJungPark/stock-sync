package org.example.stock.service

import jakarta.transaction.Transactional
import org.example.stock.repository.StockJpaRepository
import org.springframework.stereotype.Service

@Service
class PessimisticLockStockService (
    private val stockJpaRepository: StockJpaRepository
){

    @Transactional
    fun decrease(id: Long, quantity: Long) {
        val stock = stockJpaRepository.findByIdWithPessimisticLock(id)

        stock.decrease(quantity)

        stockJpaRepository.saveAndFlush(stock)
    }
}