package org.example.stock.domain

import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id

@Entity
class Stock(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    val productId: Long,
    var quantity: Long,
) {

    fun decrease(quantity: Long) {
        if (this.quantity < quantity) {
            throw RuntimeException("재고는 0개 미만이 될 수 없습니다.")
        }

        this.quantity -= quantity
    }
}