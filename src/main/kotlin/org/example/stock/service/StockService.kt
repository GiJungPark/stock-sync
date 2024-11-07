package org.example.stock.service

import org.example.stock.repository.StockJpaRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Service
class StockService (
    private val stockJpaRepository: StockJpaRepository
){

    @Transactional(propagation = Propagation.REQUIRES_NEW)
     fun decrease(id: Long, quantity: Long) {
        val stock = stockJpaRepository.findById(id).orElseThrow()

        stock.decrease(quantity)

        stockJpaRepository.saveAndFlush(stock)
    }
}