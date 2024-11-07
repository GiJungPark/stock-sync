package org.example.stock.facade

import org.example.stock.service.StockService
import org.redisson.api.RedissonClient
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

@Component
class RedissonLockStockFacade(
    private val redissonClient: RedissonClient,
    private val stockService: StockService,
) {

    fun decrease(id: Long, quantity: Long) {
        val lock = redissonClient.getLock(id.toString())

        try {
            val available = lock.tryLock(10, 1, TimeUnit.SECONDS)

            if (!available) {
                println("lock 흭득 실패")
                return
            }

            stockService.decrease(id, quantity)
        } finally {
            lock.unlock()
        }
    }
}