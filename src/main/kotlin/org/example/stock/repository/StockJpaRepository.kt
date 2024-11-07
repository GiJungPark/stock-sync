package org.example.stock.repository

import jakarta.persistence.LockModeType
import org.example.stock.domain.Stock
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query

interface StockJpaRepository : JpaRepository<Stock, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Stock s where s.id = :id")
    fun findByIdWithPessimisticLock(id: Long): Stock

    @Lock(LockModeType.OPTIMISTIC)
    @Query("select s from Stock s where s.id = :id")
    fun findByIdWithOptimisticLock(id: Long): Stock
}