package org.example.stock.repository

import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Component
import java.time.Duration

@Component
class RedisLockRepository(
    private val redisTemplate: RedisTemplate<String, String>,
) {

    fun lock(key: Long): Boolean {
        return redisTemplate
            .opsForValue()
            .setIfAbsent(privateKey(key), "lock", Duration.ofMillis(3_000))!!
    }

    fun unlock(key: Long): Boolean {
        return redisTemplate.delete(privateKey(key))
    }

    private fun privateKey(key: Long): String {
        return key.toString()
    }
}