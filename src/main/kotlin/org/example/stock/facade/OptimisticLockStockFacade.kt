package org.example.stock.facade

import org.example.stock.service.OptimisticLockStockService
import org.springframework.stereotype.Component

@Component
class OptimisticLockStockFacade(
    private val optimisticLockStockService: OptimisticLockStockService,
) {

    fun decrease(id: Long, quantity: Long) {
        while (true) {
            try {
                optimisticLockStockService.decrease(id, quantity)

                break
            } catch (e: Exception) {
                Thread.sleep(50)
            }
        }
    }

}