package org.example.stock.facade

import jakarta.transaction.Transactional
import org.example.stock.repository.LockRepository
import org.example.stock.service.StockService
import org.springframework.stereotype.Component


@Component
class NamedLockStockFacade(
    private val lockRepository: LockRepository,
    private val stockService: StockService,
) {

    @Transactional
    fun decrease(id: Long, quantity: Long) {
        try {
            lockRepository.getLock(id.toString())
            stockService.decrease(id, quantity)
        } finally {
            lockRepository.releaseLock(id.toString())
        }
    }
}